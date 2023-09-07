package org.tron.core;

import com.google.protobuf.ByteString;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.tron.common.BaseTest;
import org.tron.common.utils.ByteArray;
import org.tron.core.capsule.AccountCapsule;
import org.tron.core.config.Parameter.AdaptiveResourceLimitConstants;
import org.tron.core.config.Parameter.ChainConstant;
import org.tron.core.config.args.Args;
import org.tron.core.db.EnergyProcessor;
import org.tron.protos.Protocol.AccountType;
import org.tron.protos.contract.AssetIssueContractOuterClass.AssetIssueContract;
import org.tron.protos.contract.Common;

@Slf4j
public class EnergyProcessorTest extends BaseTest {

  private static final String ASSET_NAME;
  private static final String CONTRACT_PROVIDER_ADDRESS;
  private static final String USER_ADDRESS;

  static {
    dbPath = "energy_processor_test";
    Args.setParam(new String[]{"--output-directory", dbPath}, Constant.TEST_CONF);
    ASSET_NAME = "test_token";
    CONTRACT_PROVIDER_ADDRESS =
        Wallet.getAddressPreFixString() + "548794500882809695a8a687866e76d4271a1abc";
    USER_ADDRESS = Wallet.getAddressPreFixString() + "abd4b9367799eaa3197fecb144eb71de1e049abc";
  }

  /**
   * create temp Capsule test need.
   */
  @Before
  public void createCapsule() {
    AccountCapsule contractProvierCapsule =
        new AccountCapsule(
            ByteString.copyFromUtf8("owner"),
            ByteString.copyFrom(ByteArray.fromHexString(CONTRACT_PROVIDER_ADDRESS)),
            AccountType.Normal,
            0L);
    contractProvierCapsule.addAsset(ASSET_NAME.getBytes(), 100L);

    AccountCapsule userCapsule =
        new AccountCapsule(
            ByteString.copyFromUtf8("asset"),
            ByteString.copyFrom(ByteArray.fromHexString(USER_ADDRESS)),
            AccountType.AssetIssue,
            dbManager.getDynamicPropertiesStore().getAssetIssueFee());

    dbManager.getAccountStore().reset();
    dbManager.getAccountStore()
        .put(contractProvierCapsule.getAddress().toByteArray(), contractProvierCapsule);
    dbManager.getAccountStore().put(userCapsule.getAddress().toByteArray(), userCapsule);

  }


  //todo ,replaced by smartContract later
  private AssetIssueContract getAssetIssueContract() {
    return AssetIssueContract.newBuilder()
        .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(USER_ADDRESS)))
        .setName(ByteString.copyFromUtf8(ASSET_NAME))
        .setFreeAssetNetLimit(1000L)
        .setPublicFreeAssetNetLimit(1000L)
        .build();
  }

  @Test
  public void testUseContractCreatorEnergy() throws Exception {
    dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(1526647838000L);
    dbManager.getDynamicPropertiesStore().saveTotalEnergyWeight(10_000_000L);

    AccountCapsule ownerCapsule = dbManager.getAccountStore()
        .get(ByteArray.fromHexString(CONTRACT_PROVIDER_ADDRESS));
    dbManager.getAccountStore().put(ownerCapsule.getAddress().toByteArray(), ownerCapsule);

    EnergyProcessor processor = new EnergyProcessor(dbManager.getDynamicPropertiesStore(),
        dbManager.getAccountStore());
    long energy = 10000;
    long now = 1526647838000L;

    boolean result = processor.useEnergy(ownerCapsule, energy, now);
    Assert.assertEquals(false, result);

    ownerCapsule.setFrozenForEnergy(10_000_000L, 0L);
    result = processor.useEnergy(ownerCapsule, energy, now);
    Assert.assertEquals(true, result);

    AccountCapsule ownerCapsuleNew = dbManager.getAccountStore()
        .get(ByteArray.fromHexString(CONTRACT_PROVIDER_ADDRESS));

    Assert.assertEquals(1526647838000L, ownerCapsuleNew.getLatestOperationTime());
    Assert.assertEquals(1526647838000L,
        ownerCapsuleNew.getAccountResource().getLatestConsumeTimeForEnergy());
    Assert.assertEquals(10000L, ownerCapsuleNew.getAccountResource().getEnergyUsage());
  }

  @Test
  public void testUseEnergyInWindowSizeV2() throws Exception {
    dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(10000L);
    dbManager.getDynamicPropertiesStore().saveTotalEnergyWeight(2849288700L);
    dbManager.getDynamicPropertiesStore().saveUnfreezeDelayDays(14);

    AccountCapsule ownerCapsule =
        dbManager.getAccountStore().get(ByteArray.fromHexString(CONTRACT_PROVIDER_ADDRESS));
    ownerCapsule.setNewWindowSize(Common.ResourceCode.ENERGY, 300);
    ownerCapsule.setWindowOptimized(Common.ResourceCode.ENERGY, false);
    ownerCapsule.setLatestConsumeTimeForEnergy(9999L);
    ownerCapsule.setEnergyUsage(70021176L);
    dbManager.getAccountStore().put(ownerCapsule.getAddress().toByteArray(), ownerCapsule);

    EnergyProcessor processor =
        new EnergyProcessor(dbManager.getDynamicPropertiesStore(), dbManager.getAccountStore());
    long energy = 2345L;
    long now = 9999L;
    for (int i = 0; i < 1000; i++) {
      processor.useEnergy(ownerCapsule, energy, now);
    }
    processor.useEnergy(ownerCapsule, energy, now);
    Assert.assertEquals(72368521, ownerCapsule.getEnergyUsage());
    Assert.assertEquals(300, ownerCapsule.getWindowSize(Common.ResourceCode.ENERGY));
    Assert.assertFalse(ownerCapsule.getWindowOptimized(Common.ResourceCode.ENERGY));

    dbManager.getDynamicPropertiesStore().saveAllowCancelAllUnfreezeV2(1);
    ownerCapsule.setNewWindowSize(Common.ResourceCode.ENERGY, 300);
    ownerCapsule.setWindowOptimized(Common.ResourceCode.ENERGY, false);
    ownerCapsule.setLatestConsumeTimeForEnergy(9999L);
    ownerCapsule.setEnergyUsage(70021176L);
    dbManager.getAccountStore().put(ownerCapsule.getAddress().toByteArray(), ownerCapsule);

    for (int i = 0; i < 1000; i++) {
      processor.useEnergy(ownerCapsule, energy, now);
    }
    processor.useEnergy(ownerCapsule, energy, now);

    Assert.assertEquals(72368521L, ownerCapsule.getEnergyUsage());
    Assert.assertEquals(1224, ownerCapsule.getWindowSize(Common.ResourceCode.ENERGY));
    Assert.assertEquals(1224919, ownerCapsule.getWindowSizeV2(Common.ResourceCode.ENERGY));
    Assert.assertTrue(ownerCapsule.getWindowOptimized(Common.ResourceCode.ENERGY));

    ownerCapsule.setNewWindowSize(Common.ResourceCode.ENERGY, 300);
    ownerCapsule.setWindowOptimized(Common.ResourceCode.ENERGY, false);
    ownerCapsule.setLatestConsumeTimeForEnergy(9999L);
    ownerCapsule.setEnergyUsage(70021176L);
    dbManager.getAccountStore().put(ownerCapsule.getAddress().toByteArray(), ownerCapsule);
    for (int i = 0; i < 1000; i++) {
      processor.useEnergy(ownerCapsule, energy, now);
      now++;
    }
    Assert.assertEquals(15844971L, ownerCapsule.getEnergyUsage());
    Assert.assertEquals(2086, ownerCapsule.getWindowSize(Common.ResourceCode.ENERGY));
    Assert.assertEquals(2086556, ownerCapsule.getWindowSizeV2(Common.ResourceCode.ENERGY));
  }

  @Test
  public void updateAdaptiveTotalEnergyLimit() {
    EnergyProcessor processor = new EnergyProcessor(dbManager.getDynamicPropertiesStore(),
        dbManager.getAccountStore());

    // open
    dbManager.getDynamicPropertiesStore().saveAllowAdaptiveEnergy(1);

    // Test resource usage auto reply
    dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(1526647838000L);
    long now = chainBaseManager.getHeadSlot();
    dbManager.getDynamicPropertiesStore().saveTotalEnergyAverageTime(now);
    dbManager.getDynamicPropertiesStore().saveTotalEnergyAverageUsage(4000L);

    dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(
        1526647838000L + AdaptiveResourceLimitConstants.PERIODS_MS / 2);
    processor.updateTotalEnergyAverageUsage();
    Assert.assertEquals(2000L,
        dbManager.getDynamicPropertiesStore().getTotalEnergyAverageUsage());

    // test saveTotalEnergyLimit
    long ratio = ChainConstant.WINDOW_SIZE_MS / AdaptiveResourceLimitConstants.PERIODS_MS;
    dbManager.getDynamicPropertiesStore().saveTotalEnergyLimit(10000L * ratio);
    Assert.assertEquals(1000L,
        dbManager.getDynamicPropertiesStore().getTotalEnergyTargetLimit());

    //Test exceeds resource limit
    dbManager.getDynamicPropertiesStore().saveTotalEnergyCurrentLimit(10000L * ratio);
    dbManager.getDynamicPropertiesStore().saveTotalEnergyAverageUsage(3000L);
    processor.updateAdaptiveTotalEnergyLimit();
    Assert.assertEquals(10000L * ratio,
        dbManager.getDynamicPropertiesStore().getTotalEnergyCurrentLimit());

    //Test exceeds resource limit 2
    dbManager.getDynamicPropertiesStore().saveTotalEnergyCurrentLimit(20000L * ratio);
    dbManager.getDynamicPropertiesStore().saveTotalEnergyAverageUsage(3000L);
    processor.updateAdaptiveTotalEnergyLimit();
    Assert.assertEquals(20000L * ratio * 99 / 100L,
        dbManager.getDynamicPropertiesStore().getTotalEnergyCurrentLimit());

    //Test less than resource limit
    dbManager.getDynamicPropertiesStore().saveTotalEnergyCurrentLimit(20000L * ratio);
    dbManager.getDynamicPropertiesStore().saveTotalEnergyAverageUsage(500L);
    processor.updateAdaptiveTotalEnergyLimit();
    Assert.assertEquals(20000L * ratio * 1000 / 999L,
        dbManager.getDynamicPropertiesStore().getTotalEnergyCurrentLimit());
  }


}
