# User Guide: Stores Management System

Welcome to the **Stores Management System** User Guide. This document is designed to help you navigate the frontend interface, complete daily operational tasks, and manage both central and site-level stores effectively. 

This guide is organized by the main sections you will find in your left-hand navigation sidebar.

---

## 1. Overview

### Dashboard
The **Dashboard** is your central hub. When you log in, this is the first screen you will see. It provides an at-a-glance view of the system's health, tailored to your specific role.
*   **Quick Stats:** View metrics like total pending material requests, incoming receipts, active projects, and items that have hit their low-stock thresholds.
*   **Alerts & Actions:** Use quick action buttons to jump straight into resolving pending approvals or logging item consumption.

---

## 2. Warehouse Management

This section is primarily used by Central Store Managers and System Administrators to manage the physical flow of goods into and out of the warehouse.

### Items
The **Items** page is your central product catalog. 
*   **Viewing Stock:** You can search and filter the list of all items. The table displays exactly how much physical stock is *Available*, *Reserved* (allocated but not yet dispatched), *Incoming* (on expected receipts), and *Frozen* (locked due to an ongoing discrepancy).
*   **Adding Items:** Click **"Add Item"** to register new materials into the catalog, specifying categories, measuring units, and reorder thresholds.

### Expected Receipts & GRN
When a purchase order is placed with a supplier, you track its arrival here.
*   **Tracking:** Click **"Add Expected Receipt"** to notify the system of an upcoming delivery. You can update the status (e.g., *In Transit*, *Delayed*) as you get updates from the supplier.
*   **Confirming GRN:** When the physical delivery truck arrives, open the receipt and click **"Confirm GRN"**. You will be able to enter the exact quantities received and flag any items that arrived damaged.

### Issues & Dispatch
The **Dispatch** page manages the physical handover of stock that has been requested and approved.
*   **Dispatching Stock:** Once a Material Request is approved, it appears here. When the transport or employee arrives to collect the goods, you record the "Collector Name" and process the dispatch, permanently removing the items from your warehouse inventory and marking them as *In Transit*.

### Returns
The **Returns** page handles unused or damaged materials sent back from site stores or projects.
*   **Receiving Returns:** When a site returns materials, they will appear here as *Awaiting Confirmation*. Upon physical inspection, you must **Confirm** the return, specifying how much was actually received and whether it is serviceable or damaged.

### Discrepancies
If there is a mismatch between what was expected and what physically arrived (either during a GRN delivery or a Return), the missing quantities are routed here.
*   **Resolving Variances:** Managers investigate discrepancies on this page. Once resolved, you must mark the discrepancy as either **"Recovered"** (the goods were found and returned to stock) or **"Written Off"** (the goods are permanently lost).

---

## 3. Operations

This section bridges the gap between the Central Warehouse and the active Site Stores.

### Material Requests
Site Store Managers use this page to request materials needed for their projects.
*   **Creating a Request:** Click **"New Request"** to start drafting an order. You will select the target Project and source Store, and then add specific line items and quantities to the request.
*   **Approval Flow:** Once submitted, the Central Store Manager must review the request. They can approve the requested quantities (or adjust them downward based on available stock) or reject the request entirely.

### Consumption
Used by Site Store Managers to record the actual usage of materials on the ground.
*   **Logging Usage:** Find an item in your site's inventory and click **"Consume"**. You will specify the exact quantity used, the date of use, and any contextual notes (e.g., "Used for Block C foundation"). This accurately deducts the physical stock from the site.

### Stock Counts
To ensure system records match physical reality, periodic stock counts (audits) are performed here.
*   **Initiating a Count:** Select a store and initiate a stock count. The system will take a snapshot of the expected inventory.
*   **Entering Counts:** Staff will physically count the items on the shelves and enter the physical numbers into the system.
*   **Adjustments:** If variances are found, an authorized manager can raise automatic **Stock Adjustments** directly from this page to align the system records with the physical count.

---

## 4. Projects

### Managing Projects
The **Projects** page is where organizational initiatives are tracked.
*   **Creating Projects:** Define new projects, allocate budgets, and assign them to specific Site Stores.
*   **Assigning Employees:** Open a project to assign specific employees to it, ensuring that material issue vouchers can be correctly tracked to the individuals working on site.
*   **Closing Projects:** Once a project concludes, closing it will automatically initiate a return process for any remaining stock held at its dedicated site store.

---

## 5. Administration

System Administrators and Executive Management use these pages to configure the system and review compliance.

### Stores & Users
*   **Stores:** Create new Central or Site stores and assign Managers to oversee them.
*   **Users:** Register new system users and assign them specific Role permissions (e.g., *Site Store Manager*, *Procurement Officer*).

### Suppliers
Maintain a directory of approved vendors. You can also view **Supplier Performance** reports to analyze metrics like average lead times, fulfillment accuracy, and damage rates based on historical Expected Receipts and GRNs.

### Reports & Audit Log
*   **Reports:** Generate and download PDF or CSV reports for inventory levels, project consumption, and financial audits.
*   **Audit Log:** A read-only ledger that meticulously tracks every critical action taken in the system, detailing *who* did *what* and *when*, ensuring total accountability.

---
*Tip: Pay attention to the notification badges next to menu items in the sidebar—they indicate items that require your immediate attention, such as pending requests or unconfirmed returns.*
