import { createContext, useContext, useState, useMemo, useEffect, useRef, useCallback } from "react";
import { apiFetch } from "../api";

const AppDataContext = createContext(null);

// Mirrors backend/.../user/Role.java exactly. This is a read-only reference
// (e.g. for the "Add User" role checkboxes) — it is NOT a client-side
// permission switch. The only thing that actually decides what a user can
// do is the roles returned by GET /api/auth/me plus each endpoint's
// @PreAuthorize on the server; nothing on the client can change that.
const ROLES = [
  "SYSTEM_ADMINISTRATOR",
  "CENTRAL_STORE_MANAGER",
  "SITE_STORE_MANAGER",
  "PROCUREMENT_OFFICER",
  "FINANCE",
  "EXECUTIVE_MANAGEMENT",
];

// backend/.../movement/MaterialRequestStatus.java: DRAFT, PENDING_APPROVAL,
// APPROVED, REJECTED, IN_TRANSIT, COMPLETED, DISCREPANCY. There is no
// "DISPATCHED" or "RECEIVED" status — dispatch() moves a request straight to
// IN_TRANSIT, and receive() resolves it to COMPLETED or DISCREPANCY.
const MATERIAL_REQUEST_STATUS_LABEL = {
  DRAFT: "Draft",
  PENDING_APPROVAL: "Pending Approval",
  APPROVED: "Approved",
  REJECTED: "Rejected",
  IN_TRANSIT: "In Transit",
  COMPLETED: "Received",
  DISCREPANCY: "Received (Discrepancy)",
};

function mapMaterialRequest(r) {
  return {
    id: r.id,
    requestNo: r.referenceNumber || r.id.substring(0, 8).toUpperCase(),
    project: r.project?.name || r.project?.code || "Unknown Project",
    requestedBy: r.raisedBy?.fullName || "Unknown",
    status: MATERIAL_REQUEST_STATUS_LABEL[r.status] || r.status,
    // A request can carry multiple item lines; this is a display summary,
    // not a single flat item/quantity (which the entity doesn't have).
    lines: (r.lines || []).map((l) => ({
      item: l.item?.name || "Unknown item",
      uom: l.item?.unitOfMeasure || "",
      requested: l.requestedQuantity,
      approved: l.approvedQuantity,
      dispatched: l.dispatchedQuantity,
      received: l.receivedQuantity,
    })),
    original: r,
  };
}

// backend/.../receipt/ExpectedReceiptStatus.java has 9 values; the UI only
// has 3 visual stages. AWAITING_GRN is the backend's default status for a
// brand-new receipt (see ExpectedReceipt.java field initializer) — it means
// "ready for goods receiving", i.e. stage 1 (Arrived), not stage 0.
const RECEIPT_STAGE = {
  DRAFT: 0,
  SUBMITTED: 0,
  SUPPLIER_CONFIRMED: 0,
  IN_TRANSIT: 0,
  DELAYED: 0,
  CANCELLED: 0,
  AWAITING_GRN: 1,
  PARTIALLY_RECEIVED: 2,
  COMPLETED: 2,
};

// Non-terminal forward transitions PATCH /{id}/status is allowed to make.
// COMPLETED/PARTIALLY_RECEIVED can only be reached via POST /{id}/confirm
// (the backend's updateStatus endpoint explicitly rejects those two).
const RECEIPT_NEXT_STATUS = {
  DRAFT: "SUBMITTED",
  SUBMITTED: "SUPPLIER_CONFIRMED",
  SUPPLIER_CONFIRMED: "IN_TRANSIT",
  IN_TRANSIT: "AWAITING_GRN",
  DELAYED: "AWAITING_GRN",
};

function mapExpectedReceipt(r) {
  return {
    id: r.id,
    receiptNo: r.id.substring(0, 8).toUpperCase(),
    supplier: r.supplierName,
    eta: r.expectedDate,
    statusIndex: RECEIPT_STAGE[r.status] ?? 0,
    backendStatus: r.status,
    original: r,
  };
}

const RETURN_STATUS_LABEL = {
  PENDING_CONFIRMATION: "Awaiting Confirmation",
  CONFIRMED: "Confirmed",
};

function mapReturn(r) {
  return {
    id: r.id,
    returnNo: "RT-" + r.id.substring(0, 8).toUpperCase(),
    project: r.miv?.project?.name || "Store Closure",
    status: RETURN_STATUS_LABEL[r.status] || r.status,
    original: r,
  };
}

function mapStockCount(c) {
  const lines = c.lines || [];
  return {
    id: c.id,
    countNo: "SC-" + c.id.substring(0, 8).toUpperCase(),
    scope: c.store?.name || "Warehouse",
    date: c.createdAt.substring(0, 10),
    // backend/.../count/StockCountStatus.java: only OPEN or COMPLETED.
    status: c.status === "COMPLETED" ? "Posted" : "Counting",
    original: c,
    snapshot: lines.map((l) => ({
      lineId: l.id,
      code: l.item?.code || "",
      name: l.item?.name || "Unknown Item",
      expected: l.systemQuantitySnapshot,
    })),
    counted: lines.reduce((acc, l) => {
      if (l.physicalQuantity !== null && l.physicalQuantity !== undefined && l.item) {
        acc[l.item.code] = l.physicalQuantity;
      }
      return acc;
    }, {}),
  };
}

const asList = (res) => (Array.isArray(res) ? res : res?.content || []);

export function AppDataProvider({ children }) {
  const [items, setItems] = useState([]);
  const [expectedReceipts, setExpectedReceipts] = useState([]);
  const [materialRequests, setMaterialRequests] = useState([]);
  const [discrepancies, setDiscrepancies] = useState([]);
  const [returnsList, setReturnsList] = useState([]);
  const [suppliers, setSuppliers] = useState([]);
  const [projects, setProjects] = useState([]);
  const [users, setUsers] = useState([]);
  const [stockCounts, setStockCounts] = useState([]);
  const [batches, setBatches] = useState([]);

  const [auditLog, setAuditLog] = useState([]);
  const [supplierPerformance, setSupplierPerformance] = useState([]);
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);
  const [defaultStoreId, setDefaultStoreId] = useState(null);
  const [stores, setStores] = useState([]);

  function mapItem(i, stockByCode) {
    const stock = stockByCode[i.code] || { onHand: 0, reserved: 0, inTransit: 0, frozen: 0 };
    const reorderPoint = Number(i.reorderThreshold || 0);
    const available = Math.max(0, stock.onHand - stock.reserved);
    const lowStock = available <= reorderPoint;
    return {
      id: i.id,
      code: i.code,
      name: i.name,
      category: typeof i.category === "string" ? i.category : i.category?.name,
      available,
      reserved: stock.reserved,
      incoming: stock.inTransit,
      frozen: stock.frozen,
      reorderPoint,
      status: lowStock
        ? { label: "Low Stock", type: "danger" }
        : { label: "In Stock", type: "success" },
      original: i,
    };
  }

  async function refreshItems() {
    const [fetchedItems, fetchedStock] = await Promise.all([
      apiFetch("/api/items?size=2000").catch(() => []),
      // GET /api/reports/current-stock — Item itself has no quantity field;
      // stock lives per-store in StoreInventory. This report joins the two.
      apiFetch("/api/reports/current-stock").catch(() => []),
    ]);
    const stockRows = asList(fetchedStock);
    const stockByCode = {};
    stockRows.forEach((row) => {
      const agg = stockByCode[row.itemCode] || { onHand: 0, reserved: 0, inTransit: 0, frozen: 0 };
      agg.onHand += Number(row.onHand || 0);
      agg.reserved += Number(row.reserved || 0);
      agg.inTransit += Number(row.inTransit || 0);
      agg.frozen += Number(row.frozen || 0);
      stockByCode[row.itemCode] = agg;
    });
    setItems(asList(fetchedItems).map((i) => mapItem(i, stockByCode)));
  }

  async function addItem(itemPayload) {
    try {
      await apiFetch("/api/items", { method: "POST", body: itemPayload });
      await refreshItems();
    } catch (e) {
      console.error(e);
      throw e;
    }
  }

  async function consumeItems(storeId, lines) {
    try {
      // lines may include { itemId, quantity, consumedAt, notes } — the API
      // accepts consumedAt (ISO date string) and notes as optional fields
      await apiFetch(`/api/stores/${storeId}/consume`, {
        method: "POST",
        body: { lines },
      });
      await refreshItems();
    } catch (e) {
      console.error("Failed to consume items", e);
      throw e;
    }
  }

  async function addStore(storePayload) {
    try {
      await apiFetch("/api/stores", { method: "POST", body: storePayload });
      const [openStoresResponse, closedStoresResponse] = await Promise.all([
        apiFetch("/api/stores?size=200&active=true").catch(() => ({ content: [] })),
        apiFetch("/api/stores?size=200&active=false").catch(() => ({ content: [] })),
      ]);
      setStores([...asList(openStoresResponse), ...asList(closedStoresResponse)]);
    } catch (e) {
      console.error(e);
      throw e;
    }
  }


  const isFetchingRef = useRef(false);
  const lastFetchTimeRef = useRef(0);

  const refreshAll = useCallback(async () => {
    if (isFetchingRef.current || Date.now() - lastFetchTimeRef.current < 10000) return;
    isFetchingRef.current = true;
    try {
        const me = await apiFetch("/api/auth/me");
        setUser(me);

        // Fetch every store (open and closed) once, up front, so every page
        // that needs a store picker reads from real data instead of each
        // silently defaulting to defaultStoreId or hardcoding options.
        const [openStoresResponse, closedStoresResponse] = await Promise.all([
          apiFetch("/api/stores?size=200&active=true").catch(() => ({ content: [] })),
          apiFetch("/api/stores?size=200&active=false").catch(() => ({ content: [] })),
        ]);
        const storeList = [...asList(openStoresResponse), ...asList(closedStoresResponse)];
        setStores(storeList);

        // GET /api/auth/me returns assignedStoreId/assignedStoreName as flat
        // fields on the record (AuthDtos.MeResponse), not a nested object.
        let sId = me.assignedStoreId || asList(openStoresResponse)[0]?.id || null;
        setDefaultStoreId(sId);

        const [
          fetchedItems,
          fetchedStock,
          fetchedReceipts,
          fetchedRequests,
          fetchedDiscrepancies,
          fetchedUsers,
          fetchedProjects,
          fetchedSuppliers,
          fetchedReturns,
          fetchedStockCounts,
          fetchedBatches,
          fetchedAuditLog,
          fetchedSupplierPerf,
        ] = await Promise.all([
          apiFetch("/api/items?size=2000").catch(() => []),
          apiFetch("/api/reports/current-stock").catch(() => []),
          apiFetch("/api/expected-receipts").catch(() => []),
          apiFetch("/api/material-requests").catch(() => []),
          apiFetch("/api/discrepancies").catch(() => []),
          apiFetch("/api/users").catch(() => []),
          apiFetch("/api/projects").catch(() => []),
          apiFetch("/api/suppliers").catch(() => []),
          apiFetch("/api/returns").catch(() => []),
          apiFetch("/api/stock-counts").catch(() => []),
          apiFetch("/api/batches").catch(() => []),

          // Gated to SYSTEM_ADMINISTRATOR/FINANCE/EXECUTIVE_MANAGEMENT on the
          // backend (see AuditLogController) — other roles get [] via catch,
          // which is correct: they genuinely aren't allowed to see this.
          apiFetch("/api/audit-log?size=100").catch(() => []),
          apiFetch("/api/reports/supplier-performance").catch(() => []),
        ]);

        const stockRows = asList(fetchedStock);
        const stockByCode = {};
        stockRows.forEach((row) => {
          const agg = stockByCode[row.itemCode] || { onHand: 0, reserved: 0, inTransit: 0, frozen: 0 };
          agg.onHand += Number(row.onHand || 0);
          agg.reserved += Number(row.reserved || 0);
          agg.inTransit += Number(row.inTransit || 0);
          agg.frozen += Number(row.frozen || 0);
          stockByCode[row.itemCode] = agg;
        });
        setItems(asList(fetchedItems).map((i) => mapItem(i, stockByCode)));

        setExpectedReceipts(asList(fetchedReceipts).map(mapExpectedReceipt));
        setMaterialRequests(asList(fetchedRequests).map(mapMaterialRequest));

        setProjects(
          asList(fetchedProjects).map((p) => ({
            id: p.id,
            code: p.code,
            name: p.name,
            manager: p.siteStore?.manager?.fullName || (p.assignedEmployees?.length > 0 ? p.assignedEmployees[0].fullName : "Unassigned"),
            budget: p.budgetCeiling != null ? p.budgetCeiling : 100000,
            spent: 0, // Project entity doesn't track spend directly; not fabricated further than that.
            status: p.active ? "Active" : "Completed",
            original: p,
          }))
        );

        // Discrepancy has no "ref"/"type"/"diff" fields — those were made up.
        // Real shape: item, dispatchedQuantity, receivedQuantity,
        // frozenQuantity, status (OPEN/UNDER_INVESTIGATION/RESOLVED).
        setDiscrepancies(asList(fetchedDiscrepancies));

        setSuppliers(asList(fetchedSuppliers));

        setUsers(
          asList(fetchedUsers).map((u) => ({
            id: u.id,
            fullName: u.fullName,
            email: u.email,
            roles: u.roles || [],
            store: u.assignedStore?.name || "Unassigned",
            active: u.active,
            locked: u.locked,
            original: u,
          }))
        );

        setStockCounts(asList(fetchedStockCounts).map(mapStockCount));
        setReturnsList(asList(fetchedReturns).map(mapReturn));
        setBatches(asList(fetchedBatches));
        setAuditLog(asList(fetchedAuditLog));
        setSupplierPerformance(asList(fetchedSupplierPerf));
        lastFetchTimeRef.current = Date.now();
      } catch (e) {
        console.error("Failed to load application data", e);
      } finally {
        isFetchingRef.current = false;
      }
  }, []);

  // Fetch initial data on mount and on window focus
  useEffect(() => {
    refreshAll().finally(() => setLoading(false));

    const handleFocus = () => refreshAll();
    window.addEventListener("focus", handleFocus);
    return () => window.removeEventListener("focus", handleFocus);
  }, [refreshAll]);

  // --- Expected Receipts / GRN flow ---
  async function addExpectedReceipt({ supplier, eta, itemId, quantity, storeId }) {
    try {
      const lineItemId = itemId || items[0]?.id;
      const targetStoreId = storeId || defaultStoreId;
      if (!lineItemId || !targetStoreId) {
        console.error("Cannot create expected receipt: no item or store available");
        return;
      }
      const payload = {
        storeId: targetStoreId,
        supplierName: supplier || "Unknown Supplier",
        expectedDate: eta || new Date().toISOString().slice(0, 10),
        lines: [{ itemId: lineItemId, expectedQuantity: Number(quantity) || 100 }],
      };
      await apiFetch("/api/expected-receipts", { method: "POST", body: payload });
      const updated = await apiFetch("/api/expected-receipts");
      setExpectedReceipts(asList(updated).map(mapExpectedReceipt));
    } catch (e) {
      console.error(e);
    }
  }

  async function advanceReceiptStatus(receiptId) {
    try {
      const receipt = expectedReceipts.find((r) => r.receiptNo === receiptId || r.id === receiptId);
      if (!receipt) return;
      const next = RECEIPT_NEXT_STATUS[receipt.backendStatus];
      if (!next) {
        // Already at AWAITING_GRN (or a terminal/manual status) — the only
        // further action is Confirm GRN, which this endpoint won't allow.
        console.warn(`No further status update available for ${receipt.backendStatus}; use Confirm GRN instead.`);
        return;
      }
      await apiFetch(`/api/expected-receipts/${receipt.id}/status`, {
        method: "PATCH",
        body: { status: next },
      });
      const updated = await apiFetch("/api/expected-receipts");
      setExpectedReceipts(asList(updated).map(mapExpectedReceipt));
    } catch (e) {
      console.error(e);
    }
  }

  // POST /{id}/confirm — omitting the body receives every line in full at
  // GOOD condition (the backend's own default for a single-click confirm).
  // The backend auto-creates a Discrepancy for any line with variance and
  // updates StoreInventory itself; there is nothing to compute client-side.
  async function confirmGRN(receiptId, lineOverrides) {
    try {
      const receipt = expectedReceipts.find((r) => r.receiptNo === receiptId || r.id === receiptId);
      const id = receipt ? receipt.id : receiptId;
      const body = lineOverrides && lineOverrides.length ? { lines: lineOverrides } : undefined;
      await apiFetch(`/api/expected-receipts/${id}/confirm`, { method: "POST", body });
      const [updatedReceipts, updatedDiscrepancies] = await Promise.all([
        apiFetch("/api/expected-receipts"),
        apiFetch("/api/discrepancies").catch(() => []),
      ]);
      setExpectedReceipts(asList(updatedReceipts).map(mapExpectedReceipt));
      setDiscrepancies(asList(updatedDiscrepancies));
      await refreshItems();
    } catch (e) {
      console.error(e);
      throw e;
    }
  }

  // --- Material Requests ---
  async function addMaterialRequest(request) {
    try {
      const proj = request.projectId ? projects.find((p) => p.id === request.projectId) : projects[0];
      if (!proj) {
        console.error("Cannot create material request: no project available");
        return;
      }

      const payload = {
        requestingStoreId: request.requestingStoreId || defaultStoreId,
        sourceStoreId: request.sourceStoreId || defaultStoreId,
        projectId: proj.id,
        transferReason: request.notes || "Requested via UI",
        lines: request.lines || [{ itemId: request.itemId, requestedQuantity: request.quantity || 10 }],
      };
      const created = await apiFetch("/api/material-requests", { method: "POST", body: payload });
      // A new MR starts as DRAFT; submit() moves it to PENDING_APPROVAL.
      await apiFetch(`/api/material-requests/${created.id}/submit`, { method: "POST" });
      const updated = await apiFetch("/api/material-requests");
      setMaterialRequests(asList(updated).map(mapMaterialRequest));
    } catch (e) {
      console.error(e);
      throw e;
    }
  }

  async function approveRequest(id) {
    try {
      const req = materialRequests.find((r) => r.requestNo === id || r.id === id);
      const uuid = req ? req.id : id;
      await apiFetch(`/api/material-requests/${uuid}/approve`, { method: "POST" });
      const updated = await apiFetch("/api/material-requests");
      setMaterialRequests(asList(updated).map(mapMaterialRequest));
    } catch (e) {
      console.error(e);
    }
  }

  async function rejectRequest(id, reason) {
    try {
      const req = materialRequests.find((r) => r.requestNo === id || r.id === id);
      const uuid = req ? req.id : id;
      await apiFetch(`/api/material-requests/${uuid}/reject`, {
        method: "POST",
        body: { reason: reason || "Rejected" },
      });
      const updated = await apiFetch("/api/material-requests");
      setMaterialRequests(asList(updated).map(mapMaterialRequest));
    } catch (e) {
      console.error(e);
      throw e;
    }
  }

  // dispatch() moves the request straight to IN_TRANSIT (there's no separate
  // "Dispatched" status) and deducts stock server-side.
  async function dispatchRequest(id, collectorName, collectorEmployeeId) {
    try {
      const req = materialRequests.find((r) => r.requestNo === id || r.id === id);
      const uuid = req ? req.id : id;
      const payload = {
        collectorName: collectorName || "Unnamed Collector",
        collectorEmployeeId: collectorEmployeeId || "N/A",
        dispatchedQuantities: null,
      };
      await apiFetch(`/api/material-requests/${uuid}/dispatch`, { method: "POST", body: payload });
      const updated = await apiFetch("/api/material-requests");
      setMaterialRequests(asList(updated).map(mapMaterialRequest));
      await refreshItems();
    } catch (e) {
      console.error(e);
    }
  }

  // receive() resolves the request to COMPLETED or DISCREPANCY.
  async function markRequestReceived(id) {
    try {
      const req = materialRequests.find((r) => r.requestNo === id || r.id === id);
      const uuid = req ? req.id : id;
      await apiFetch(`/api/material-requests/${uuid}/receive`, { method: "POST" });
      const [updated, updatedDiscrepancies] = await Promise.all([
        apiFetch("/api/material-requests"),
        apiFetch("/api/discrepancies").catch(() => []),
      ]);
      setMaterialRequests(asList(updated).map(mapMaterialRequest));
      setDiscrepancies(asList(updatedDiscrepancies));
    } catch (e) {
      console.error(e);
    }
  }

  // --- Discrepancies ---
  // POST /{id}/resolve requires { resolutionNotes, recovered }. "recovered"
  // means the stock was actually found (returned to on-hand); otherwise it's
  // a permanent write-off. There is no separate WRITTEN_OFF/RECOVERED
  // DiscrepancyStatus — resolving always moves status to RESOLVED, and
  // whether it was a recovery lives in resolutionNotes/the inventory ledger.
  async function resolveDiscrepancy(id, recovered, notes) {
    try {
      await apiFetch(`/api/discrepancies/${id}/resolve`, {
        method: "POST",
        body: { resolutionNotes: notes || (recovered ? "Recovered" : "Written off"), recovered: !!recovered },
      });
      const updated = await apiFetch("/api/discrepancies");
      setDiscrepancies(asList(updated));
      await refreshItems();
    } catch (e) {
      console.error(e);
    }
  }

  // --- Returns ---
  async function confirmReturn(returnId) {
    try {
      const req = returnsList.find((r) => r.returnNo === returnId || r.id === returnId);
      const uuid = req ? req.id : returnId;
      await apiFetch(`/api/returns/${uuid}/confirm`, { method: "POST" });
      const updated = await apiFetch("/api/returns");
      setReturnsList(asList(updated).map(mapReturn));
      await refreshItems();
    } catch (e) {
      console.error(e);
    }
  }

  async function initiateReturn(requestId, payloadLines) {
    try {
      const req = materialRequests.find((r) => r.requestNo === requestId || r.id === requestId);
      const uuid = req ? req.id : requestId;
      await apiFetch(`/api/material-requests/${uuid}/returns`, {
        method: "POST",
        body: { lines: payloadLines },
      });
      const updatedReturns = await apiFetch("/api/returns");
      setReturnsList(asList(updatedReturns).map(mapReturn));
    } catch (e) {
      console.error(e);
      throw e;
    }
  }

  // --- Projects ---
  async function addProject(project) {
    try {
      const siteStoreId = project.siteStoreId || defaultStoreId;
      if (!siteStoreId) {
        console.error("Cannot create project: no site store selected and no store on this user");
        return;
      }
      const payload = {
        code: project.code,
        name: project.name,
        siteStoreId,
        budgetCeiling: project.budget || 50000,
      };
      await apiFetch("/api/projects", { method: "POST", body: payload });
      const updated = await apiFetch("/api/projects");
      setProjects(
        asList(updated).map((p) => ({
          id: p.id,
          code: p.code,
          name: p.name,
          manager: p.siteStore?.manager?.fullName || (p.assignedEmployees?.length > 0 ? p.assignedEmployees[0].fullName : "Unassigned"),
          budget: p.budgetCeiling || 100000,
          spent: 0,
          status: p.active ? "Active" : "Completed",
          original: p,
        }))
      );
    } catch (e) {
      console.error(e);
    }
  }

  // PUT /api/projects/{id}/close — the backend cascades this to close the
  // project's dedicated site store too (if no other active project shares
  // it), so both projects and stores need refreshing afterward.
  async function closeProject(projectId) {
    try {
      await apiFetch(`/api/projects/${projectId}/close`, { method: "PUT" });
      const [updatedProjects, openStores, closedStores] = await Promise.all([
        apiFetch("/api/projects"),
        apiFetch("/api/stores?size=200&active=true").catch(() => ({ content: [] })),
        apiFetch("/api/stores?size=200&active=false").catch(() => ({ content: [] })),
      ]);
      setProjects(
        asList(updatedProjects).map((p) => ({
          id: p.id,
          code: p.code,
          name: p.name,
          manager: p.siteStore?.manager?.fullName || (p.assignedEmployees?.length > 0 ? p.assignedEmployees[0].fullName : "Unassigned"),
          budget: p.budgetCeiling || 100000,
          spent: 0,
          status: p.active ? "Active" : "Completed",
          original: p,
        }))
      );
      setStores([...asList(openStores), ...asList(closedStores)]);
    } catch (e) {
      console.error(e);
      throw e;
    }
  }

  // POST/DELETE /api/projects/{id}/employees/{employeeId} — this was
  // previously just a link to the generic Users page with no way to
  // actually attach someone to a specific project.
  async function assignEmployeeToProject(projectId, employeeId) {
    try {
      await apiFetch(`/api/projects/${projectId}/employees/${employeeId}`, { method: "POST" });
      const updated = await apiFetch("/api/projects");
      setProjects(
        asList(updated).map((p) => ({
          id: p.id,
          code: p.code,
          name: p.name,
          manager: p.siteStore?.manager?.fullName || (p.assignedEmployees?.length > 0 ? p.assignedEmployees[0].fullName : "Unassigned"),
          budget: p.budgetCeiling || 100000,
          spent: 0,
          status: p.active ? "Active" : "Completed",
          original: p,
        }))
      );
    } catch (e) {
      console.error(e);
      throw e;
    }
  }

  async function removeEmployeeFromProject(projectId, employeeId) {
    try {
      await apiFetch(`/api/projects/${projectId}/employees/${employeeId}`, { method: "DELETE" });
      const updated = await apiFetch("/api/projects");
      setProjects(
        asList(updated).map((p) => ({
          id: p.id,
          code: p.code,
          name: p.name,
          manager: p.siteStore?.manager?.fullName || (p.assignedEmployees?.length > 0 ? p.assignedEmployees[0].fullName : "Unassigned"),
          budget: p.budgetCeiling || 100000,
          spent: 0,
          status: p.active ? "Active" : "Completed",
          original: p,
        }))
      );
    } catch (e) {
      console.error(e);
    }
  }

  // --- Suppliers ---
  async function addSupplier(supplier) {
    try {
      await apiFetch("/api/suppliers", { method: "POST", body: supplier });
      const updated = await apiFetch("/api/suppliers");
      setSuppliers(asList(updated));
    } catch (e) {
      console.error(e);
    }
  }

  async function updateSupplier(id, patch) {
    try {
      await apiFetch(`/api/suppliers/${id}`, { method: "PUT", body: patch });
      const updated = await apiFetch("/api/suppliers");
      setSuppliers(asList(updated));
    } catch (e) {
      console.error(e);
    }
  }

  // --- Users ---
  // POST /api/users requires roles as exact Role enum names
  // (e.g. "SYSTEM_ADMINISTRATOR") — Role.valueOf(...) throws on anything else.
  async function addUser({ fullName, email, temporaryPassword, roles, assignedStoreId }) {
    try {
      const payload = {
        fullName,
        email,
        temporaryPassword: temporaryPassword || "ChangeMe123!",
        roles,
        assignedStoreId: assignedStoreId || null,
      };
      await apiFetch("/api/users", { method: "POST", body: payload });
      const updated = await apiFetch("/api/users");
      setUsers(
        asList(updated).map((u) => ({
          id: u.id,
          fullName: u.fullName,
          email: u.email,
          roles: u.roles || [],
          store: u.assignedStore?.name || "Unassigned",
          active: u.active,
          locked: u.locked,
          original: u,
        }))
      );
    } catch (e) {
      console.error(e);
      throw e;
    }
  }

  // --- Stock Counts ---
  async function initiateStockCount(storeId) {
    try {
      const targetStoreId = storeId || defaultStoreId;
      if (!targetStoreId) {
        console.error("Cannot initiate stock count: no store selected and no store on this user");
        return;
      }
      const newCount = await apiFetch("/api/stock-counts", { method: "POST", body: { storeId: targetStoreId } });
      const updated = await apiFetch("/api/stock-counts");
      setStockCounts(asList(updated).map(mapStockCount));
      return newCount;
    } catch (e) {
      console.error("Initiate count error", e);
      throw e;
    }
  }

  async function submitCount(countId, code, countedValue) {
    try {
      const count = stockCounts.find((c) => c.countNo === countId || c.id === countId);
      if (!count) return;
      const line = count.original.lines.find((l) => l.item.code === code);
      if (!line) return;
      await apiFetch(`/api/stock-counts/${count.original.id}/lines/${line.id}`, {
        method: "PUT",
        body: { physicalQuantity: countedValue },
      });
      setStockCounts((rows) =>
        rows.map((c) => (c.id === count.id ? { ...c, counted: { ...c.counted, [code]: countedValue } } : c))
      );
    } catch (e) {
      console.error("Submit count error", e);
    }
  }

  async function postStockCount(countId) {
    try {
      const count = stockCounts.find((c) => c.countNo === countId || c.id === countId);
      if (!count) return;
      const uuid = count.original.id;
      // Only variant lines need an adjustment raised; zero-variance lines
      // resolve themselves server-side once physically counted.
      for (const line of count.original.lines) {
        const physical = count.counted[line.item.code];
        if (physical !== undefined && Number(physical) !== Number(line.systemQuantitySnapshot)) {
          try {
            await apiFetch(`/api/stock-counts/${uuid}/lines/${line.id}/adjustment`, {
              method: "POST",
              body: { notes: "Adjustment via bulk post" },
            });
          } catch (e) {
            console.error("Adjustment error for line", line.id, e);
          }
        }
      }
      const updatedCounts = await apiFetch("/api/stock-counts");
      setStockCounts(asList(updatedCounts).map(mapStockCount));
      await refreshItems();
    } catch (e) {
      console.error("Post stock count error", e);
    }
  }

  // --- Derived badge counts (single source of truth for the sidebar) ---
  const badges = useMemo(
    () => ({
      items: items.filter((i) => i.status.label === "Low Stock").length,
      expectedReceipts: expectedReceipts.filter((r) => r.statusIndex < 2).length,
      materialRequests: materialRequests.filter((r) => r.status === "Pending Approval").length,
      dispatch: materialRequests.filter((r) => r.status === "Approved").length,
      discrepancies: discrepancies.filter((d) => d.status === "OPEN").length,
      returns: returnsList.filter((r) => r.status === "Awaiting Confirmation").length,
      stockCounts: stockCounts.filter((c) => c.status === "Counting").length,
    }),
    [items, expectedReceipts, materialRequests, discrepancies, returnsList, stockCounts]
  );

  const value = {
    items,
    setItems,
    refreshItems,
    refreshAll,
    addItem,
    expectedReceipts,
    addExpectedReceipt,
    advanceReceiptStatus,
    confirmGRN,
    materialRequests,
    addMaterialRequest,
    approveRequest,
    rejectRequest,
    dispatchRequest,
    markRequestReceived,
    discrepancies,
    resolveDiscrepancy,
    returnsList,
    confirmReturn,
    suppliers,
    addSupplier,
    updateSupplier,
    projects,
    addProject,
    closeProject,
    assignEmployeeToProject,
    removeEmployeeFromProject,
    stores,
    addStore,
    users,
    addUser,
    stockCounts,
    initiateStockCount,
    submitCount,
    postStockCount,
    batches,
    auditLog,
    supplierPerformance,
    user,
    setUser,
    roles: ROLES,
    loading,
    consumeItems,
    defaultStoreId,
    badges,
    initiateReturn,
  };

  if (loading) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100vh', flexDirection: 'column' }}>
        <div style={{
          width: '50px',
          height: '50px',
          border: '4px solid #f3f3f3',
          borderTop: '4px solid #2563eb',
          borderRadius: '50%',
          animation: 'spin 1s linear infinite'
        }} />
        <style>
          {`
            @keyframes spin {
              0% { transform: rotate(0deg); }
              100% { transform: rotate(360deg); }
            }
          `}
        </style>
        <p style={{ marginTop: '16px', color: '#64748b', fontWeight: 500 }}>Loading Application...</p>
      </div>
    );
  }

  return <AppDataContext.Provider value={value}>{children}</AppDataContext.Provider>;
}

export function useAppData() {
  const ctx = useContext(AppDataContext);
  if (!ctx) throw new Error("useAppData must be used within AppDataProvider");
  return ctx;
}
