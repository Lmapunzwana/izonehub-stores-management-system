# Stock Count Workflow

This document explains how the stock count feature operates within the Inventory Management System.

## 1. Initiation
- **Who**: A Site Store Manager or Central Store Manager initiates a stock count.
- **How**: The user navigates to the **Stock Counts** page and clicks "New Stock Count".
- **Action**: The system creates a new stock count record in the database with a snapshot of the current system quantities (`systemQuantitySnapshot`) for all active items in that specific store. The status of this count is set to **OPEN** ("Counting" in the UI).

## 2. Counting Process
- The store manager physically counts the inventory on the shelves.
- They enter the physical quantities into the system for each line item.
- At this stage, the variance (the difference between the system quantity and the physical quantity) is calculated but not yet posted to the inventory ledger.

## 3. Review and Submission (Posting)
- **Who**: The store manager (or a designated approver depending on the specific store's permission model) reviews the count.
- **How**: The user clicks "Post Count" once all items have been counted.
- **Action**: 
  - The status of the stock count changes from **OPEN** to **COMPLETED** ("Posted").
  - The system automatically identifies any items where `physicalQuantity` does not match the `systemQuantitySnapshot`.

## 4. Variance Handling & Quantity Updates
- For items where there is no variance, no further action is required. The system recognizes the physical count matches the expected system count.
- For items with a variance, the system automatically triggers a **Stock Adjustment**.
- The `InventoryCommandService` explicitly adjusts the `quantity_on_hand` in the database to exactly match the newly inputted physical quantity (`adjustTo(newQuantity)`).
- An Audit Log entry is created for each adjustment, noting that the reason was a bulk stock count post, ensuring traceability of the variance.
