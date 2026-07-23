ALTER TABLE discrepancy ADD COLUMN stock_count_id UUID REFERENCES stock_count(id);
