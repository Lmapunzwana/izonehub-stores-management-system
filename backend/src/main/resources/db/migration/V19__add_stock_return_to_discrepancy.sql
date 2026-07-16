ALTER TABLE discrepancy ADD COLUMN stock_return_id UUID REFERENCES stock_return(id);
