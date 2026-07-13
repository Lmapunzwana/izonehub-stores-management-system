import { Routes, Route } from "react-router-dom";
import Layout from "./components/Layout";
import Dashboard from "./pages/Dashboard";
import ItemsPage from "./pages/ItemsPage";
import ConsumptionPage from "./pages/ConsumptionPage";
import ExpectedReceiptsPage from "./pages/ExpectedReceiptsPage";
import MaterialRequestsPage from "./pages/MaterialRequestsPage";
import AddItemToRequestPage from "./pages/AddItemToRequestPage";
import DispatchPage from "./pages/DispatchPage";
import ProjectsPage from "./pages/ProjectsPage";
import ProjectDetailsPage from "./pages/ProjectDetailsPage";
import EmployeesPage from "./pages/EmployeesPage";
import UsersPage from "./pages/UsersPage";
import SuppliersPage from "./pages/SuppliersPage";
import SupplierPerformancePage from "./pages/SupplierPerformancePage";
import ReturnsPage from "./pages/ReturnsPage";
import DiscrepanciesPage from "./pages/DiscrepanciesPage";
import StockCountsPage from "./pages/StockCountsPage";
import BatchSerialTrackingPage from "./pages/BatchSerialTrackingPage";
import ExpiryMonitoringPage from "./pages/ExpiryMonitoringPage";
import AuditLogPage from "./pages/AuditLogPage";
import ReportsPage from "./pages/ReportsPage";
import ConfirmGRNPage from "./pages/ConfirmGRNPage";
import AddItemPage from "./pages/AddItemPage";
import StoresPage from "./pages/StoresPage";
import UnauthorizedPage from "./pages/errors/UnauthorizedPage";
import ForbiddenPage from "./pages/errors/ForbiddenPage";
import NotFoundPage from "./pages/errors/NotFoundPage";
import ServerErrorPage from "./pages/errors/ServerErrorPage";
import RequireRole from "./components/RequireRole";
import RequireAuth from "./components/RequireAuth";
import LoginPage from "./pages/LoginPage";
import SubscriptionPage from "./pages/SubscriptionPage";

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="*" element={
        <RequireAuth>
          <Layout>
            <Routes>
              <Route path="/" element={<Dashboard />} />
              <Route path="/items" element={<ItemsPage />} />
              <Route path="/consumption" element={<ConsumptionPage />} />
              <Route path="/items/add-item" element={<AddItemPage />} />
              <Route path="/expected-receipts" element={<ExpectedReceiptsPage />} />
              <Route path="/material-requests" element={<MaterialRequestsPage />} />
              <Route path="/material-requests/add-item" element={<AddItemToRequestPage />} />
              <Route path="/dispatch" element={<DispatchPage />} />
              <Route path="/projects" element={<ProjectsPage />} />
              <Route path="/projects/:id" element={<ProjectDetailsPage />} />
              <Route path="/employees" element={<EmployeesPage />} />
              <Route path="/users" element={
                <RequireRole role={["SYSTEM_ADMINISTRATOR", "CENTRAL_STORE_MANAGER"]}>
                  <UsersPage />
                </RequireRole>
              } />
              <Route path="/stores" element={
                <RequireRole role={["SYSTEM_ADMINISTRATOR", "CENTRAL_STORE_MANAGER"]}>
                  <StoresPage />
                </RequireRole>
              } />
              <Route path="/subscription" element={
                <RequireRole role="SYSTEM_ADMINISTRATOR">
                  <SubscriptionPage />
                </RequireRole>
              } />
              <Route path="/returns" element={<ReturnsPage />} />
              <Route path="/discrepancies" element={<DiscrepanciesPage />} />
              <Route path="/stock-counts" element={<StockCountsPage />} />
              <Route path="/expiry-monitoring" element={<ExpiryMonitoringPage />} />
              <Route path="/audit-log" element={
                <RequireRole role={["SYSTEM_ADMINISTRATOR", "CENTRAL_STORE_MANAGER"]}>
                  <AuditLogPage />
                </RequireRole>
              } />
              <Route path="/reports" element={<ReportsPage />} />
              <Route path="/confirm-grn" element={<ConfirmGRNPage />} />
              <Route path="/401" element={<UnauthorizedPage />} />
              <Route path="/403" element={<ForbiddenPage />} />
              <Route path="/500" element={<ServerErrorPage />} />
              <Route path="*" element={<NotFoundPage />} />
            </Routes>
          </Layout>
        </RequireAuth>
      } />
    </Routes>
  );
}
