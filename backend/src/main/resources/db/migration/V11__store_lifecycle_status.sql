-- Site stores previously had no way to represent being closed out once
-- their project finished. A store row lived forever regardless of whether
-- the project it was created for was still active.
alter table store add column active boolean not null default true;
create index idx_store_active on store(active);
