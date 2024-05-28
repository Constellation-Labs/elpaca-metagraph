# Proof of Attendance Metagraph (PoAM)

## Overview

Proof of Attendance Metagraph (PoAM) is a social credit metagraph designed to track community activity within the ecosystem and reward positive behaviors with tokens.

## Metagraph

The PoAM metagraph is a straightforward, currency-based metagraph that periodically fetches data about network participation and applies minting logic based on this data.

## Data Sources

1. **Exolix Swaps:** All wallets that have swapped into DAG should get credit based on the number of swaps.
2. **Simplex Purchases:** All wallets that have purchased DAG through Simplex should get credit based on the number of purchases.
3. **IntegrationNet Node Operator Line:** Should get credit while their balance is over 250k DAG and the operator is in the queue.
4. **New Wallet Creation:** Create a new wallet with at least 1,500 DAG and hold it for 7 days.

## Token Minting Rates

- **Simplex/Exolix:** 35 per transaction/purchase
- **Lattice Node Operator Queue Wallets:** 1 per day in the queue
- **New Wallet Creation:** 10