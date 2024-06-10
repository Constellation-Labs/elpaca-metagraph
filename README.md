# Elpaca Metagraph

## Overview

The Elpaca Metagraph is a social credit metagraph designed to track community activity within the ecosystem and reward positive behaviors with tokens.

## Metagraph

The Elpaca Metagraph is a straightforward, currency-based metagraph that periodically fetches data about network participation and applies minting logic based on this data.

## Data Sources

1. **Exolix Swaps:** All wallets that have swapped into DAG will receive credit based on the number of swaps.
2. **Simplex Purchases:** All wallets that have purchased DAG through Simplex will receive credit based on the number of purchases.
3. **IntegrationNet Node Operator Line:** Wallets with a balance over 250k DAG and whose operators are in the queue will receive credit.
4. **New Wallet Creation:** Wallets created with at least 1,500 DAG and held for 7 days will receive credit.
5. **All Wallets:** All new and existing wallets will receive 1 token.

## Token Minting Rates

- **Simplex/Exolix Transactions:** 35 tokens per transaction or purchase.
- **Lattice Node Operator Queue Wallets:** 1 token per day in the queue.
- **New Wallet Creation:** 10 tokens.
- **All Wallets:** 1 token per wallet.
