package com.themistra.crypto.dev;

import com.themistra.crypto.chain.ChainAdapter;
import com.themistra.crypto.chain.ChainAdapterRegistry;
import com.themistra.crypto.chain.ChainId;
import com.themistra.crypto.chain.TxObservation;
import com.themistra.crypto.config.ChainProviderProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.http.HttpService;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Finds a real transaction to try, so there is something to paste into the box.
 *
 * <p>Local development only. Scans back from the head rather than the head itself: providers lag
 * one another by a few blocks, so a freshly-mined transaction reliably produces disagreement —
 * correct behaviour, but a confusing first impression when you are trying to see agreement work.
 */
@Service
@RestController
@RequestMapping("/dev/api")
@Profile("local")
public class DevSampleService {

    private final ChainAdapterRegistry registry;
    private final ChainProviderProperties properties;

    public DevSampleService(ChainAdapterRegistry registry, ChainProviderProperties properties) {
        this.registry = registry;
        this.properties = properties;
    }

    @GetMapping("/sample")
    public Map<String, Object> sample(@RequestParam String chainId,
                                      @RequestParam(defaultValue = "200") int blocksBack) {
        ChainId chain = ChainId.parse(chainId);
        ChainAdapter probe = registry.adaptersFor(chain).getFirst();
        Web3j web3j = Web3j.build(new HttpService(firstEndpoint(chain)));

        try {
            BigInteger head = web3j.ethBlockNumber().send().getBlockNumber();
            for (int back = blocksBack; back < blocksBack + 12; back++) {
                EthBlock.Block block = web3j
                        .ethGetBlockByNumber(
                                DefaultBlockParameter.valueOf(head.subtract(BigInteger.valueOf(back))), true)
                        .send()
                        .getBlock();
                if (block == null) {
                    continue;
                }
                for (EthBlock.TransactionResult<?> tx : block.getTransactions()) {
                    String hash = ((EthBlock.TransactionObject) tx.get()).getHash();
                    Optional<TxObservation> seen = probe.getTx(hash);
                    if (seen.isPresent() && seen.get().amount().signum() > 0) {
                        Map<String, Object> body = new LinkedHashMap<>();
                        body.put("txHash", hash);
                        body.put("blockNumber", seen.get().blockNumber());
                        body.put("blocksBehindHead", back);
                        return body;
                    }
                }
            }
            throw new IllegalStateException("no token transfer found in the scanned range");
        } catch (Exception e) {
            throw new IllegalStateException("could not scan for a sample transaction: " + e.getMessage(), e);
        }
    }

    private String firstEndpoint(ChainId chain) {
        return properties.getChains().stream()
                .filter(c -> chain.value().equals(c.getId()))
                .flatMap(c -> c.getProviders().stream())
                .map(ChainProviderProperties.Provider::getEndpoint)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("no endpoint configured for " + chain));
    }
}
