import { useRef, useState } from "react";
import * as XLSX from "xlsx";
import { X, UploadCloud, CheckCircle2, XCircle, Loader2 } from "lucide-react";
import { apiFetch } from "../api";

/**
 * Bulk item import: entirely client-side heavy lifting.
 *
 *   1. The file (.xlsx/.xls/.csv) is parsed in-browser with the `xlsx`
 *      library — nothing is uploaded as a raw file to the backend.
 *   2. Every row is validated locally before any network call is made.
 *   3. Valid rows are POSTed to the existing single-item endpoint
 *      (POST /api/items) ONE AT A TIME, awaited in sequence — deliberately
 *      not Promise.all/bulk: a slow or overloaded backend degrades to
 *      "one row at a time" rather than a burst of concurrent requests,
 *      and each row's pass/fail is independent, so one bad row doesn't
 *      sink the rest of the file.
 *   4. Progress is shown live as it goes, not just at the end.
 *
 * Expected columns (case-insensitive header match), one row per item:
 *   code, name, description, unitOfMeasure, category, reorderThreshold,
 *   initialQuantity, storeName
 * Only code/name/unitOfMeasure/category are required. storeName is
 * matched case-insensitively against the store names already loaded in
 * the app; initialQuantity is ignored if storeName doesn't resolve.
 */

const REQUIRED_COLUMNS = ["code", "name", "unitofmeasure", "category"];

function normalizeHeader(h) {
  return String(h || "").trim().toLowerCase().replace(/[\s_]/g, "");
}

function parseRows(workbook) {
  const sheet = workbook.Sheets[workbook.SheetNames[0]];
  const raw = XLSX.utils.sheet_to_json(sheet, { defval: "", raw: false });
  return raw;
}

function normalizeRow(row) {
  // Build a normalized-key lookup so "Unit of Measure", "unit_of_measure",
  // "unitOfMeasure" etc. all resolve the same way.
  const byNormalizedKey = {};
  for (const key of Object.keys(row)) {
    byNormalizedKey[normalizeHeader(key)] = row[key];
  }
  return {
    code: String(byNormalizedKey.code ?? "").trim(),
    name: String(byNormalizedKey.name ?? "").trim(),
    description: String(byNormalizedKey.description ?? "").trim() || null,
    unitOfMeasure: String(byNormalizedKey.unitofmeasure ?? "").trim(),
    category: String(byNormalizedKey.category ?? "").trim().toUpperCase(),
    reorderThreshold: byNormalizedKey.reorderthreshold,
    initialQuantity: byNormalizedKey.initialquantity,
    storeName: String(byNormalizedKey.storename ?? "").trim(),
  };
}

function validateRow(row, validCategories, storesByName) {
  const errors = [];
  if (!row.code) errors.push("missing code");
  if (!row.name) errors.push("missing name");
  if (!row.unitOfMeasure) errors.push("missing unitOfMeasure");
  if (!row.category) errors.push("missing category");
  else if (validCategories.length > 0 && !validCategories.includes(row.category)) {
    errors.push(`unknown category '${row.category}'`);
  }

  let reorderThreshold = 0;
  if (row.reorderThreshold !== undefined && row.reorderThreshold !== "") {
    reorderThreshold = Number(row.reorderThreshold);
    if (Number.isNaN(reorderThreshold) || reorderThreshold < 0) errors.push("invalid reorderThreshold");
  }

  let initialQuantity = 0;
  if (row.initialQuantity !== undefined && row.initialQuantity !== "") {
    initialQuantity = Number(row.initialQuantity);
    if (Number.isNaN(initialQuantity) || initialQuantity < 0) errors.push("invalid initialQuantity");
  }

  let storeId = null;
  if (initialQuantity > 0 && row.storeName) {
    const match = storesByName.get(row.storeName.trim().toLowerCase());
    if (!match) errors.push(`store '${row.storeName}' not found — initial quantity will be skipped`);
    else storeId = match.id;
  }

  return {
    errors,
    payload: {
      code: row.code,
      name: row.name,
      description: row.description,
      unitOfMeasure: row.unitOfMeasure,
      category: row.category,
      reorderThreshold,
      initialQuantity: storeId ? initialQuantity : 0,
      storeId,
    },
  };
}

export default function ImportItemsModal({ stores, categories, onClose, onComplete }) {
  const fileInputRef = useRef(null);
  const [stage, setStage] = useState("pick"); // pick | reviewing | importing | done
  const [rows, setRows] = useState([]);       // [{ rowNumber, payload, errors, status, resultMessage }]
  const [currentIndex, setCurrentIndex] = useState(0);
  const cancelRef = useRef(false);

  const storesByName = new Map(stores.map((s) => [s.name.trim().toLowerCase(), s]));

  function handleFileChosen(e) {
    const file = e.target.files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = (evt) => {
      try {
        const workbook = XLSX.read(evt.target.result, { type: "array" });
        const rawRows = parseRows(workbook);
        if (rawRows.length === 0) {
          setRows([]);
          setStage("reviewing");
          return;
        }
        const headerKeys = Object.keys(rawRows[0]).map(normalizeHeader);
        const missingRequired = REQUIRED_COLUMNS.filter((c) => !headerKeys.includes(c));
        if (missingRequired.length > 0) {
          setRows([{
            rowNumber: 0,
            payload: null,
            errors: [`File is missing required column(s): ${missingRequired.join(", ")}`],
            status: "invalid",
          }]);
          setStage("reviewing");
          return;
        }

        const prepared = rawRows.map((raw, i) => {
          const normalized = normalizeRow(raw);
          const { errors, payload } = validateRow(normalized, categories, storesByName);
          return {
            rowNumber: i + 2, // +2: 1-indexed, plus the header row
            payload,
            errors,
            status: errors.some((e) => !e.includes("will be skipped")) ? "invalid" : "valid",
          };
        });
        setRows(prepared);
        setStage("reviewing");
      } catch (err) {
        setRows([{ rowNumber: 0, payload: null, errors: [`Could not read file: ${err.message}`], status: "invalid" }]);
        setStage("reviewing");
      }
    };
    reader.readAsArrayBuffer(file);
  }

  async function startImport() {
    cancelRef.current = false;
    setStage("importing");
    const importable = rows.filter((r) => r.status === "valid");
    for (let i = 0; i < importable.length; i++) {
      if (cancelRef.current) break;
      const row = importable[i];
      setCurrentIndex(i + 1);
      try {
        await apiFetch("/api/items", { method: "POST", body: row.payload });
        row.status = "success";
      } catch (err) {
        row.status = "failed";
        row.resultMessage = err?.message || "Request failed";
      }
      // Force a re-render each row so progress is visibly live, not batched.
      setRows((prev) => [...prev]);
    }
    setStage("done");
    onComplete?.();
  }

  const validCount = rows.filter((r) => r.status === "valid" || r.status === "success" || r.status === "failed").length;
  const invalidCount = rows.filter((r) => r.status === "invalid").length;
  const succeededCount = rows.filter((r) => r.status === "success").length;
  const failedCount = rows.filter((r) => r.status === "failed").length;

  return (
    <div style={overlayStyle}>
      <div style={modalStyle}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16 }}>
          <h3 style={{ margin: 0, fontSize: 16 }}>Import Items</h3>
          {stage !== "importing" && (
            <button onClick={onClose} style={closeBtnStyle}><X size={18} /></button>
          )}
        </div>

        {stage === "pick" && (
          <div style={{ textAlign: "center", padding: "24px 12px" }}>
            <UploadCloud size={40} color="#94a3b8" style={{ marginBottom: 12 }} />
            <p style={{ color: "#64748b", fontSize: 13, marginBottom: 16 }}>
              Upload an Excel (.xlsx) or CSV file. Required columns: <code>code, name, unitOfMeasure, category</code>.
              Optional: <code>description, reorderThreshold, initialQuantity, storeName</code>.
            </p>
            <input
              ref={fileInputRef}
              type="file"
              accept=".xlsx,.xls,.csv"
              onChange={handleFileChosen}
              style={{ display: "block", margin: "0 auto" }}
            />
          </div>
        )}

        {stage === "reviewing" && (
          <div>
            <div style={{ marginBottom: 12, fontSize: 13, color: "#475569" }}>
              Found <strong>{rows.length}</strong> row(s) — <strong style={{ color: "#16a34a" }}>{validCount - invalidCount}</strong> ready to import,{" "}
              <strong style={{ color: "#dc2626" }}>{invalidCount}</strong> will be skipped.
            </div>
            <div style={{ maxHeight: 260, overflowY: "auto", border: "1px solid #e2e8f0", borderRadius: 8 }}>
              {rows.map((r) => (
                <div key={r.rowNumber} style={rowStyle}>
                  <span style={{ fontWeight: 600, width: 50, flexShrink: 0 }}>Row {r.rowNumber || "—"}</span>
                  <span style={{ flex: 1, fontSize: 13 }}>
                    {r.payload ? `${r.payload.code} — ${r.payload.name}` : "—"}
                    {r.errors.length > 0 && (
                      <div style={{ color: r.status === "invalid" ? "#dc2626" : "#f59e0b", fontSize: 12 }}>
                        {r.errors.join("; ")}
                      </div>
                    )}
                  </span>
                  {r.status === "invalid"
                    ? <XCircle size={16} color="#dc2626" />
                    : <CheckCircle2 size={16} color="#16a34a" />}
                </div>
              ))}
            </div>
            <div style={{ display: "flex", justifyContent: "flex-end", gap: 8, marginTop: 16 }}>
              <button className="btn" onClick={onClose}>Cancel</button>
              <button
                className="btn btn-primary"
                disabled={validCount - invalidCount === 0}
                onClick={startImport}
              >
                Import {validCount - invalidCount} Item(s)
              </button>
            </div>
          </div>
        )}

        {stage === "importing" && (
          <div style={{ textAlign: "center", padding: "24px 12px" }}>
            <Loader2 size={32} className="spin" color="#2563eb" style={{ marginBottom: 12 }} />
            <p style={{ fontSize: 14, fontWeight: 600, marginBottom: 8 }}>
              Importing {currentIndex} of {validCount - invalidCount}…
            </p>
            <div style={{ height: 8, background: "#e2e8f0", borderRadius: 4, overflow: "hidden", marginBottom: 8 }}>
              <div style={{
                height: "100%", background: "#2563eb", borderRadius: 4,
                width: `${((currentIndex) / Math.max(validCount - invalidCount, 1)) * 100}%`,
                transition: "width 0.2s ease",
              }} />
            </div>
            <p style={{ fontSize: 12, color: "#64748b" }}>Do not close this window.</p>
          </div>
        )}

        {stage === "done" && (
          <div>
            <div style={{ textAlign: "center", padding: "12px" }}>
              <CheckCircle2 size={36} color="#16a34a" style={{ marginBottom: 8 }} />
              <p style={{ fontSize: 14, fontWeight: 600 }}>
                {succeededCount} imported, {failedCount} failed, {invalidCount} skipped.
              </p>
            </div>
            {failedCount > 0 && (
              <div style={{ maxHeight: 200, overflowY: "auto", border: "1px solid #fecaca", borderRadius: 8, marginTop: 8 }}>
                {rows.filter((r) => r.status === "failed").map((r) => (
                  <div key={r.rowNumber} style={rowStyle}>
                    <span style={{ fontWeight: 600, width: 50, flexShrink: 0 }}>Row {r.rowNumber}</span>
                    <span style={{ flex: 1, fontSize: 13 }}>
                      {r.payload.code} — {r.payload.name}
                      <div style={{ color: "#dc2626", fontSize: 12 }}>{r.resultMessage}</div>
                    </span>
                  </div>
                ))}
              </div>
            )}
            <div style={{ display: "flex", justifyContent: "flex-end", marginTop: 16 }}>
              <button className="btn btn-primary" onClick={onClose}>Close</button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

const overlayStyle = {
  position: "fixed", inset: 0, background: "rgba(15, 23, 42, 0.5)",
  display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000,
};

const modalStyle = {
  background: "#fff", borderRadius: 12, padding: 24,
  width: "min(560px, 92vw)", maxHeight: "85vh", overflowY: "auto",
  boxShadow: "0 20px 50px rgba(0,0,0,0.25)",
};

const closeBtnStyle = {
  background: "none", border: "none", cursor: "pointer", color: "#64748b", padding: 4,
};

const rowStyle = {
  display: "flex", alignItems: "flex-start", gap: 8, padding: "8px 12px",
  borderBottom: "1px solid #f1f5f9",
};
