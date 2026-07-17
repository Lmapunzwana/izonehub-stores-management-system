# Inventory Management System - Workflows

This document outlines the core operational workflows for handling inventory in the system.

## 1. Material Requests

**Goal:** A site needs materials for an ongoing project.
**Actor:** Site Store Manager
1. Navigate to **Material Requests** -> **New Request**.
2. Select the relevant **Project**. The system automatically sets the requesting store to the site store dedicated to that project.
3. Select the **Source Store**. While this usually defaults to the Central Warehouse, site managers can choose another site store for site-to-site transfers.
4. Add items and quantities to the request. The system immediately checks live stock availability to ensure sufficient inventory.
5. Submit the request.
6. *Background*: The system immediately **reserves** the requested stock in the source store, reducing its available quantity so it cannot be double-booked.

## 2. Approving Material Requests

**Goal:** Review and authorize requested materials.
**Actor:** Central Store Manager
1. Review pending requests on the **Material Requests** dashboard.
2. Click **View Items** to inspect the full list of requested materials.
3. Choose to **Approve** or **Reject** the request.
    - If **Approved**: The manager can optionally adjust the approved quantities downwards. Any difference between requested and approved quantities is unreserved, returning to available stock.
    - If **Rejected**: The manager must provide a reason. All reserved stock is immediately unreserved and returned to the available pool.

## 3. Dispatching Materials

**Goal:** Physically move authorized materials to the requesting site.
**Actor:** Source Store Manager (usually Central Store Manager)
1. Navigate to the **Dispatch** page to see approved requests awaiting dispatch.
2. Prepare the materials and fill out the dispatch form with the **Collector's Name** and **Employee ID**.
3. Confirm dispatch.
4. *Background*: The system moves the stock from *Reserved* status to *In-Transit* status.
5. Print the auto-generated **Dispatch Note** PDF, which includes signatures for the requester, issuer (approver), and collector.

## 4. Receiving Materials

**Goal:** Acknowledge receipt of materials at the destination site.
**Actor:** Site Store Manager
1. Upon physical arrival, locate the corresponding In-Transit request in the system.
2. Confirm receipt.
3. *Background*: The stock is moved from *In-Transit* into the Site Store's *On-Hand* inventory.
    - If there is a missing quantity (short delivery), the system flags the request as **Received (Discrepancy)**, freezing the missing units until an investigation resolves them as either recovered or permanently written off.

## 5. Consuming Materials

**Goal:** Log materials that have been utilized on-site for a project.
**Actor:** Site Store Manager
1. Navigate to the **Consumption** page.
2. Select the specific **Site Store** (Project).
3. The page displays only the physical inventory currently available at that site.
4. Click **Consume** next to the used item.
5. Enter the quantity used, date of consumption, and optional notes (e.g., "Used in foundation pouring for Block A").
6. *Background*: The system deducts the consumed quantity from the site's physical inventory and logs it against the project's material usage.
