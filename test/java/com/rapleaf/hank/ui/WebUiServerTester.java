package com.rapleaf.hank.ui;

import com.rapleaf.hank.ZkTestCase;
import com.rapleaf.hank.coordinator.*;
import com.rapleaf.hank.generated.HankBulkResponse;
import com.rapleaf.hank.generated.HankResponse;
import com.rapleaf.hank.generated.SmartClient.Iface;
import com.rapleaf.hank.partition_assigner.PartitionAssigner;
import com.rapleaf.hank.partition_assigner.UniformPartitionAssigner;
import org.apache.thrift.TException;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WebUiServerTester extends ZkTestCase {
  public void testIt() throws Exception {
    final Coordinator coordinator = getMockCoordinator();

    // Assign
    PartitionAssigner partitionAssigner = new UniformPartitionAssigner();
    RingGroup rgAlpha = coordinator.getRingGroup("rgAlpha");
    DomainGroupVersion g1v1 = coordinator.getDomainGroup("g1").getVersionByNumber(1);
    partitionAssigner.assign(g1v1, rgAlpha.getRing(1));
    partitionAssigner.assign(g1v1, rgAlpha.getRing(2));
    partitionAssigner.assign(g1v1, rgAlpha.getRing(3));
    // Set updated partitions
    int frequency = 1;
    for (Host host : rgAlpha.getRing(1).getHosts()) {
      ++frequency;
      for (HostDomain hostDomain : host.getAssignedDomains()) {
        for (HostDomainPartition partition : hostDomain.getPartitions()) {
          if (partition.getPartitionNumber() % frequency == 0) {
            partition.setCurrentDomainGroupVersion(partition.getUpdatingToDomainGroupVersion());
            partition.setUpdatingToDomainGroupVersion(null);
          }
        }
      }
    }

    final Iface mockClient = new Iface() {
      private final Map<String, ByteBuffer> values = new HashMap<String, ByteBuffer>() {
        {
          put("key1", ByteBuffer.wrap("value1".getBytes()));
          put("key2", ByteBuffer.wrap("a really long value that you will just love!".getBytes()));
        }
      };

      @Override
      public HankResponse get(String domainName, ByteBuffer key) throws TException {
        String sKey = null;
        try {
          sKey = new String(key.array(), key.position(), key.limit(), "UTF-8");
        } catch (UnsupportedEncodingException e) {
          throw new TException(e);
        }

        ByteBuffer v = values.get(sKey);
        if (v != null) {
          return HankResponse.value(v);
        }

        return HankResponse.not_found(true);
      }

      @Override
      public HankBulkResponse getBulk(String domainName, List<ByteBuffer> keys) throws TException {
        return null;
      }
    };
    IClientCache clientCache = new IClientCache() {
      @Override
      public Iface getSmartClient(RingGroup rgc) throws IOException, TException {
        return mockClient;
      }
    };
    WebUiServer uiServer = new WebUiServer(coordinator, clientCache, 12345);
    uiServer.run();
  }
}
