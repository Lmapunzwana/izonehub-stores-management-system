-- Patch 0007: Central approval gate for site-to-site transfers.
-- MaterialRequest.centralApprovedBy maps to this column (nullable — only set
-- for requests that pass through PENDING_CENTRAL_APPROVAL, i.e. transfers
-- where neither the source nor the requesting store is Central).
ALTER TABLE material_request
    ADD COLUMN central_approved_by_id uuid REFERENCES app_users(id);
