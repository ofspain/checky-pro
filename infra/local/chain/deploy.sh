#!/bin/sh
# Deploys MockUSDC as anvil account 0's first transaction, so it always lands at
# 0x5FbDB2315678afecb367f032d93F642f64180aa3. Does nothing when the code is already there.
set -eu
RPC=http://anvil:8545
TOKEN=0x5FbDB2315678afecb367f032d93F642f64180aa3
# anvil's well-known development key for account 0; it controls nothing outside this local chain.
KEY=0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80
if [ "$(cast code "$TOKEN" --rpc-url "$RPC")" != "0x" ]; then
  echo "MockUSDC already deployed at $TOKEN"; exit 0
fi
cd /chain
forge create MockUSDC.sol:MockUSDC --rpc-url "$RPC" --private-key "$KEY" --broadcast --use 0.8.24
# Anvil account 2 is the sender local screening blocks (config/crypto.properties); give it USDC.
cast send "$TOKEN" "transfer(address,uint256)" 0x3C44CdDdB6a900fa2b585dd299e03d12FA4293BC 1000000000000 \
  --rpc-url "$RPC" --private-key "$KEY" >/dev/null
echo "MockUSDC deployed at $TOKEN"
