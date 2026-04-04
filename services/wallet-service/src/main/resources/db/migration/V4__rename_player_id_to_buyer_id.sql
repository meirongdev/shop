-- V4: Rename player_id to buyer_id for naming consistency
ALTER TABLE wallet_account RENAME COLUMN player_id TO buyer_id;
ALTER TABLE wallet_transaction RENAME COLUMN player_id TO buyer_id;
