package org.tron.core.services.filter;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import java.io.File;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.tron.api.GrpcAPI;
import org.tron.api.WalletGrpc;
import org.tron.api.WalletSolidityGrpc;
import org.tron.common.application.Application;
import org.tron.common.application.ApplicationFactory;
import org.tron.common.application.TronApplicationContext;
import org.tron.common.utils.FileUtil;
import org.tron.common.utils.PublicMethod;
import org.tron.core.ChainBaseManager;
import org.tron.core.Constant;
import org.tron.core.config.DefaultConfig;
import org.tron.core.config.args.Args;
import org.tron.core.services.RpcApiService;
import org.tron.core.services.interfaceOnPBFT.RpcApiServiceOnPBFT;
import org.tron.core.services.interfaceOnSolidity.RpcApiServiceOnSolidity;

@Slf4j
public class LiteFnQueryGrpcInterceptorTest {

  private TronApplicationContext context;
  private ManagedChannel channelFull = null;
  private ManagedChannel channelpBFT = null;
  private WalletGrpc.WalletBlockingStub blockingStubFull = null;
  private WalletSolidityGrpc.WalletSolidityBlockingStub blockingStubSolidity = null;
  private WalletSolidityGrpc.WalletSolidityBlockingStub blockingStubpBFT = null;
  private RpcApiService rpcApiService;
  private RpcApiServiceOnSolidity rpcApiServiceOnSolidity;
  private RpcApiServiceOnPBFT rpcApiServiceOnPBFT;
  private Application appTest;
  private ChainBaseManager chainBaseManager;

  private String dbPath = "output_grpc_interceptor_test";

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  /**
   * init logic.
   */
  @Before
  public void init() {
    Args.setParam(new String[]{"-d", dbPath}, Constant.TEST_CONF);
    Args.getInstance().setRpcPort(PublicMethod.chooseRandomPort());
    Args.getInstance().setRpcOnSolidityPort(PublicMethod.chooseRandomPort());
    Args.getInstance().setRpcOnPBFTPort(PublicMethod.chooseRandomPort());
    String fullnode = String.format("%s:%d", Args.getInstance().getNodeDiscoveryBindIp(),
            Args.getInstance().getRpcPort());
    String pBFTNode = String.format("%s:%d", Args.getInstance().getNodeDiscoveryBindIp(),
            Args.getInstance().getRpcOnPBFTPort());
    channelFull = ManagedChannelBuilder.forTarget(fullnode)
            .usePlaintext()
            .build();
    channelpBFT = ManagedChannelBuilder.forTarget(pBFTNode)
            .usePlaintext()
            .build();
    context = new TronApplicationContext(DefaultConfig.class);
    blockingStubFull = WalletGrpc.newBlockingStub(channelFull);
    blockingStubSolidity = WalletSolidityGrpc.newBlockingStub(channelFull);
    blockingStubpBFT = WalletSolidityGrpc.newBlockingStub(channelpBFT);
    blockingStubSolidity = WalletSolidityGrpc.newBlockingStub(channelFull);
    rpcApiService = context.getBean(RpcApiService.class);
    rpcApiServiceOnSolidity = context.getBean(RpcApiServiceOnSolidity.class);
    rpcApiServiceOnPBFT = context.getBean(RpcApiServiceOnPBFT.class);
    chainBaseManager = context.getBean(ChainBaseManager.class);
    appTest = ApplicationFactory.create(context);
    appTest.addService(rpcApiService);
    appTest.addService(rpcApiServiceOnSolidity);
    appTest.addService(rpcApiServiceOnPBFT);
    appTest.initServices(Args.getInstance());
    appTest.startServices();
    appTest.startup();
  }

  /**
   * destroy the context.
   */
  @After
  public void destroy() throws InterruptedException {
    if (channelFull != null) {
      channelFull.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
    if (channelpBFT != null) {
      channelpBFT.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
    Args.clearParam();
    appTest.shutdownServices();
    appTest.shutdown();
    context.destroy();
    if (FileUtil.deleteDir(new File(dbPath))) {
      logger.info("Release resources successful.");
    } else {
      logger.info("Release resources failure.");
    }
  }

  @Test
  public void testGrpcApiThrowStatusRuntimeException() {
    final GrpcAPI.NumberMessage message = GrpcAPI.NumberMessage.newBuilder().setNum(0).build();
    chainBaseManager.setNodeType(ChainBaseManager.NodeType.LITE);
    thrown.expect(StatusRuntimeException.class);
    thrown.expectMessage("UNAVAILABLE: this API is closed because this node is a lite fullnode");
    blockingStubFull.getBlockByNum(message);
  }

  @Test
  public void testpBFTGrpcApiThrowStatusRuntimeException() {
    final GrpcAPI.NumberMessage message = GrpcAPI.NumberMessage.newBuilder().setNum(0).build();
    chainBaseManager.setNodeType(ChainBaseManager.NodeType.LITE);
    thrown.expect(StatusRuntimeException.class);
    thrown.expectMessage("UNAVAILABLE: this API is closed because this node is a lite fullnode");
    blockingStubpBFT.getBlockByNum(message);
  }

  @Test
  public void testGrpcInterceptor() {
    GrpcAPI.NumberMessage message = GrpcAPI.NumberMessage.newBuilder().setNum(0).build();
    chainBaseManager.setNodeType(ChainBaseManager.NodeType.FULL);
    Assert.assertNotNull(blockingStubFull.getBlockByNum(message));
  }
}
