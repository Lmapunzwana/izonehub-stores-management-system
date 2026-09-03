# Specifications — Site-to-Site Transfers, Returns, Discrepancies, Store Visibility

Author: Leroy Mapunzwana <lmapunzwana@gmail.com>
Status: Draft for implementation (patches below implement items 1–5)

---

## 1. Central approval on site-to-site Material Requests

**Current behaviour (as of the second revision):** for a transfer where
neither the source nor the requesting store is Central, Central Store
Manager approval is a hard second gate. Dispatch — and therefore the
dispatch note PDF, which only ever gets generated as a side effect of a
successful dispatch — is blocked until Central explicitly approves, on top
of the source store manager's own approval.

**Revision history, for context:**
- First pass (superseded): visibility only — Central got copied on submit,
  but the source store manager's approval alone was enough to unblock
  dispatch.
- Current: Central approval is now required and blocking, per direct
  instruction. The visibility notification from the first pass is kept as
  well (submit-time FYI), plus a second, distinct notification once the
  source manager has approved and it's specifically Central's turn to act.

**Spec (implemented):**
- New status `PENDING_CENTRAL_APPROVAL` between the source manager's
  approval and `APPROVED`. `MaterialRequest.approve()` routes here instead
  of straight to `APPROVED` whenever `sourceStore.type != CENTRAL &&
  requestingStore.type != CENTRAL`.
- `POST /{id}/central-approve` (Central/Admin only) is the only way out of
  `PENDING_CENTRAL_APPROVAL`, into `APPROVED`.
- `dispatch()` refuses anything not already `APPROVED`, with a clear message
  when the reason is a pending Central approval.
- `reject()` works from either `PENDING_APPROVAL` or
  `PENDING_CENTRAL_APPROVAL`, so Central can also kill a transfer at their
  stage via the existing reject endpoint — releasing the stock reservation
  at the source store either way.
- Requests where either side is Central (normal restocks from Central, and
  the return-to-central flow from item 2) are entirely unaffected — they
  never enter `PENDING_CENTRAL_APPROVAL` and keep the original single-
  approval behaviour.
- UI: the Issues & Dispatch page shows Central/Admin an Approve/Reject
  action directly on `PENDING_CENTRAL_APPROVAL` rows, with a Details toggle
  revealing the requesting store, who raised it, the source store, the
  source manager who already signed off, and the full item/quantity
  breakdown. Site managers see the same row as a read-only "waiting on
  Central" state with no dispatch controls.

---

## 2. Returns — consolidate on one documented path

**Clarified business rule:** all returns land at Central, never back at the
originating site store, regardless of which store the material was sourced
from. Confirmed by the existing `ReturnToCentralModal` UI, which hard-filters
its destination dropdown to `type === "CENTRAL"`.

**Problem:** the codebase has two parallel return mechanisms that don't behave
the same way:

| Path | Trail produced | Goes through dispatch note? |
|---|---|---|
| `POST /material-requests/standalone-return` (`standaloneReturn`) | Full `MaterialRequest` lifecycle: create → submit → auto-approve → auto-dispatch → (Central) receive | **Yes** — dispatch note PDF generated, receive/discrepancy handling included |
| `POST /material-requests/{id}/returns` (`recordReturn`) → `StockReturn`/`ReturnController.confirm` | Ad-hoc `StockReturn`: dispatch→confirm, inventory updated directly | **No** — no document generated, single "confirm" click silently moves stock |

Both end up at Central (once the fallback-to-CENTRAL logic in
`ReturnCommandService.confirm()` is understood correctly — that part was
already right, not a bug). The real gap is that `recordReturn` skips the
paper trail the business wants: "the dispatch note is created and when they
arrive at central store that is cleared."

**Spec:** `recordReturn` (returning against a specific, already-fulfilled
Material Request) should produce the same kind of record as
`standaloneReturn` — i.e. it should go through the MR lifecycle rather than
the ad-hoc `StockReturn` object, so every return, no matter which button
started it, produces a dispatch note and requires an explicit Central
receive/confirm step. Implemented as Patch 5 below by having `recordReturn`
build and drive a `MaterialRequest` (`requestingStore` = Central,
`sourceStore` = the store giving material back) instead of a bare
`StockReturn`.

---

## 3. New page: stock-by-store matrix for Central/Admin

**Requirement:** a screen where Central can see, per item, how much sits at
every store — e.g. Jinko Solar Panel: 120 @ Groove, 200 @ Central, 20 @ Sabi,
1 @ Murombedzi — rather than the existing Items page, which only ever shows
one store's inventory at a time via a dropdown.

**Data source:** `GET /api/inventory/site-inventory` already returns every
store's stock row when called with no `storeId` (permitted for
`CENTRAL_STORE_MANAGER`/`SYSTEM_ADMINISTRATOR`). No backend change needed —
this is a frontend-only build.

**Spec:**
- New page `StockByStorePage.jsx`, linked from the nav for
  `CENTRAL_STORE_MANAGER`/`SYSTEM_ADMINISTRATOR` only.
- Fetch `/api/inventory/site-inventory` (no `storeId`), group client-side by
  item, pivot stores into columns, add a total column.
- Search-by-item-name filter; stores below a configurable low-stock threshold
  highlighted.
- Read-only — no write actions live here.

---

## 4. Discrepancy resolution — stock-count-originated discrepancies can't clear

**Root cause (confirmed):** `StoreInventory.releaseFrozen()` requires
`quantityFrozen >= qty`. Freezing only happens for GRN variance and in-transit
variance (Material Request receive / Return receive). Stock-count variances
create a `Discrepancy` row with a `frozenQuantity` value but never actually
call `freezeTransitVariance`/`freezeGrnVariance`, so `quantityFrozen` for that
store/item is 0 (or unrelated). Resolving via
`DiscrepancyController.resolve()` then throws `IllegalStateException:
"Insufficient frozen stock"`, the transaction rolls back, and the user sees a
failed-to-resolve error no matter what they enter.

Separately, raising an adjustment from the Stock Count screen
(`raiseAdjustment`) correctly fixes the inventory, but never touches the
linked `Discrepancy` row, so it stays `OPEN` and keeps escalating to Central
every 48 hours even after the count was reconciled.

**Spec (Patch 2):**
- `StockCountController.enterCount()`: when a variance is detected, actually
  freeze the variance amount (`inventory.freezeTransitVariance` /
  equivalent) at the store before creating the `Discrepancy`, so the amount
  the `Discrepancy` claims is frozen really is.
- `StockCountController.raiseAdjustment()`: after applying the adjustment,
  find and auto-resolve any `OPEN` `Discrepancy` linked to that stock count
  + item (mark `RESOLVED`, release the frozen amount as recovered, note
  "resolved automatically via stock adjustment").
- Net effect: the Discrepancy screen's Resolve button starts working for
  count-originated discrepancies, and raising an adjustment closes the loop
  without a second manual step.

---

## 5. Store creation doesn't auto-assign the manager

**Root cause (confirmed):** `StoreController.create()` sets `Store.manager`,
but never sets `manager.assignedStore`. Most authorization checks
(`MaterialRequestController.approve()`, in particular) check
`AppUser.getAssignedStore()` directly rather than the more complete
`StoreRepository.findStoresForUser()` query (which already accounts for
`manager`, `assignedStore`, and project assignment together). So a freshly
appointed store manager can't approve requests for their own store until
someone visits the Users page and manually assigns them via the dropdown.

**Spec (Patch 1):**
- On store creation, if the selected manager has no `assignedStore` yet,
  auto-set `manager.assignedStore = savedStore`. (If they already have one —
  because they manage another store too — leave it alone; overwriting it
  would break their existing assignment. This is a known limitation of the
  single-`assignedStore`-field model for managers running multiple stores,
  noted below.)
- **Follow-up spec, not built in this patch:** `MaterialRequestController.
  approve()` should check `stores.findStoresForUser(approver.getId())`
  instead of `approver.getAssignedStore()` alone, since that query already
  correctly handles multi-store managers. This is the real fix for managers
  who run more than one store; Patch 1 only fixes the single-store case
  (the common one) at creation time.
