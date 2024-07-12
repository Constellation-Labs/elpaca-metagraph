# Elpaca Metagraph

## Overview

The Elpaca Metagraph is a social credit metagraph designed to track community activity within the ecosystem and reward positive behaviors with tokens. By incentivizing network participation, the Elpaca Metagraph aims to foster a more engaged and active community.

## Metagraph Functionality

The Elpaca Metagraph periodically fetches data about network participation and applies minting logic based on this data.

## Data Sources and Workers

We have several data sources that are executed using workers/daemons, which are triggered every hour. These workers check for new events, wallets, transactions, and other relevant data. Upon detecting new data, the workers send updates to the metagraph.

Additionally, there are manual data sources that require a `DataUpdate` to be sent manually to the `/data` endpoint.

### Daemon/Worker Data Sources

1. **Exolix Swaps:**
    - **Description:** All wallets that have swapped into DAG will receive credit based on the number of swaps.
    - **Worker Function:** The Exolix worker checks for new swap transactions every hour and updates the metagraph accordingly.

2. **Simplex Purchases:**
    - **Description:** All wallets that have purchased DAG through Simplex will receive credit based on the number of purchases.
    - **Worker Function:** The Simplex worker checks for new purchase transactions every hour and updates the metagraph accordingly.

3. **IntegrationNet Node Operator Line:**
    - **Description:** Wallets with a balance over 250k DAG and whose operators are in the queue will receive credit.
    - **Worker Function:** The IntegrationNet worker checks the queue status and wallet balances every hour and updates the metagraph accordingly.

4. **New Wallet Creation:**
    - **Description:** Wallets created with at least 1,500 DAG and held for 7 days will receive credit.
    - **Worker Function:** The New Wallet worker checks for new wallet creations and their holding status every hour and updates the metagraph accordingly.

5. **Inflow Transactions:**
   - **Description:** Provided wallets that received transactions great os equal an specific amount of DAG, greater than starting date
   - **Worker Function:** The worker checks if the provided wallets received any transactions greater than the specified amount of DAG every 30 minutes and updates the metagraph accordingly.

6. **Outflow Transactions:**
   - **Description:** Provided wallets that sent transactions great os equal an specific amount of DAG, greater than starting date.
   - **Worker Function:** The worker checks if the provided wallets received any transactions greater than the specified amount of DAG every 30 minutes and updates the metagraph accordingly.

### Manual Data Sources

1. **All Wallets:**
    - **Description:** All new and existing wallets will receive 1 token. For retroactive/existing wallets, the tokens will be distributed automatically. However, for new wallets, you need to provide an update to the metagraph to process the wallet and distribute the token.
    - **Expected Update:** For new wallets, send the following update to the metagraph:

```json
{
  "value": {
    "FreshWalletUpdate": {
      "address": ":wallet_valid_address"
    }
  },
  "proofs": [
    {
      "id": ":public_key",
      "signature": ":signature"
    }
  ]
}
```

**NOTE: Only one update per wallet will be accepted. If you provide the same wallet again, it will be discarded.**

## Token Minting Rates

- **Simplex/Exolix Transactions:** 35 tokens per transaction or purchase.
- **IntegrationNet Node Operator Queue Wallets:** 1 token per day in the queue.
- **New Wallet Creation:** 10 tokens.
- **All Wallets:** 1 token per wallet.
- **Inflow Transactions:** Defined in configuration.
- **Outflow Transactions:** Defined in configuration.

## Worker/Daemon Functionality

Some data source has an associated worker/daemon that performs the following tasks every hour:

1. **Check for Updates:** The worker queries the relevant data source for any new events, wallets, transactions, etc.
2. **Process Data:** The worker processes the fetched data to determine eligibility for rewards.
3. **Send Updates:** The worker sends the processed data to the metagraph, updating it with new information and minting tokens as necessary.

This hourly checking mechanism ensures that the metagraph remains up-to-date with the latest network activity, accurately rewarding community members for their participation.


## Conclusion

The Elpaca Metagraph leverages these data sources and workers to create a dynamic and responsive system that rewards positive behavior within the ecosystem. By continuously monitoring and updating the metagraph, we ensure that the community's efforts are recognized and incentivized.
