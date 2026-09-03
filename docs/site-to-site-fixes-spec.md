# Specifications — Site-to-Site Transfers, Returns, Discrepancies, Store Visibility

Author: Leroy Mapunzwana <lmapunzwana@gmail.com>
Status: Draft for implementation (patches below implement items 1–5)

---

## 1. Central visibility on site-to-site Material Requests

**Current behaviour:** `MaterialRequestCommandService.submit()` only notifies
`sourceStore.getManager()`. If store A requests from store B (B is not
Central), Central receives no notification at all and has no approval role
unless a Central Store Manager happens to also be B's assigned manager.

**Decision:** Visibility, not a second approval gate. Central should always
know a site-to-site transfer is happening, but the approving authority stays
with the source store's manager (per the existing approve() enforcement).
Adding a hard second approval step would slow down transfers between two
site stores for no operational benefit and isn't what was described — the
requirement was "central must know", not "central must sign off on every
site transfer."

**Spec:**
- On submit, if `sourceStore.getType() != CENTRAL`, also notify every active
  `CENTRAL_STORE_MANAGER` (cc-style, informational) with the same subject/body
  the source manager gets, plus a note that they are being copied because the
  transfer doesn't touch Central stock.
- No new status, no new approval step, no schema change.
- If the business later decides Central must gate these approvals too, that's
  a bigger follow-up (new `PENDING_CENTRAL_APPROVAL` status) — flagged as
  future work, not built now.

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
