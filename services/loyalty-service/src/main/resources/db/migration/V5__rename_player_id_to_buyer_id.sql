-- V5: Rename player_id to buyer_id for naming consistency
ALTER TABLE loyalty_account RENAME COLUMN player_id TO buyer_id;
ALTER TABLE loyalty_transaction RENAME COLUMN player_id TO buyer_id;
ALTER TABLE loyalty_checkin RENAME COLUMN player_id TO buyer_id;
ALTER TABLE loyalty_redemption RENAME COLUMN player_id TO buyer_id;
ALTER TABLE onboarding_task_progress RENAME COLUMN player_id TO buyer_id;
