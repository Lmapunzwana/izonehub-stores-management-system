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
              <Route path="/items" element={
                <RequireRole role={["SYSTEM_ADMINISTRATOR", "CENTRAL_STORE_MANAGER", "SITE_STORE_MANAGER"]}>
                  <ItemsPage />
                </RequireRole>
              } />
              <Route path="/consumption" element={
                <RequireRole role={["SYSTEM_ADMINISTRATOR", "SITE_STORE_MANAGER"]}>
                  <ConsumptionPage />
                </RequireRole>
              } />
              <Route path="/items/add-item" element={
                <RequireRole role={["SYSTEM_ADMINISTRATOR", "CENTRAL_STORE_MANAGER"]}>
                  <AddItemPage />
                </RequireRole>
              } />
              <Route path="/expected-receipts" element={
                <RequireRole role={["SYSTEM_ADMINISTRATOR", "CENTRAL_STORE_MANAGER"]}>
                  <ExpectedReceiptsPage />
                </RequireRole>
              } />
              <Route path="/material-requests" element={
                <RequireRole role={["SYSTEM_ADMINISTRATOR", "CENTRAL_STORE_MANAGER", "SITE_STORE_MANAGER"]}>
                  <MaterialRequestsPage />
                </RequireRole>
              } />
              <Route path="/material-requests/add-item" element={
                <RequireRole role={["SYSTEM_ADMINISTRATOR", "CENTRAL_STORE_MANAGER", "SITE_STORE_MANAGER"]}>
                  <AddItemToRequestPage />
                </RequireRole>
              } />
              <Route path="/dispatch" element={
                <RequireRole role={["SYSTEM_ADMINISTRATOR", "CENTRAL_STORE_MANAGER", "SITE_STORE_MANAGER"]}>
                  <DispatchPage />
                </RequireRole>
              } />
              <Route path="/projects" element={
                <RequireRole role={["SYSTEM_ADMINISTRATOR", "CENTRAL_STORE_MANAGER", "SITE_STORE_MANAGER"]}>
                  <ProjectsPage />
                </RequireRole>
              } />
              <Route path="/projects/:id" element={
                <RequireRole role={["SYSTEM_ADMINISTRATOR", "CENTRAL_STORE_MANAGER", "SITE_STORE_MANAGER"]}>
                  <ProjectDetailsPage />
                </RequireRole>
              } />
              <Route path="/employees" element={
                <RequireRole role={["SYSTEM_ADMINISTRATOR", "CENTRAL_STORE_MANAGER"]}>
                  <EmployeesPage />
                </RequireRole>
              } />
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
              <Route path="/returns" element={
                <RequireRole role={["SYSTEM_ADMINISTRATOR", "CENTRAL_STORE_MANAGER", "SITE_STORE_MANAGER"]}>
                  <ReturnsPage />
                </RequireRole>
              } />
              <Route path="/discrepancies" element={
                <RequireRole role={["SYSTEM_ADMINISTRATOR", "CENTRAL_STORE_MANAGER", "SITE_STORE_MANAGER"]}>
                  <DiscrepanciesPage />
                </RequireRole>
              } />
              <Route path="/stock-counts" element={
                <RequireRole role={["SYSTEM_ADMINISTRATOR", "CENTRAL_STORE_MANAGER", "SITE_STORE_MANAGER"]}>
                  <StockCountsPage />
                </RequireRole>
              } />
              <Route path="/audit-log" element={
                <RequireRole role={["SYSTEM_ADMINISTRATOR", "CENTRAL_STORE_MANAGER"]}>
                  <AuditLogPage />
                </RequireRole>
              } />
              <Route path="/reports" element={
                <RequireRole role={["SYSTEM_ADMINISTRATOR", "CENTRAL_STORE_MANAGER"]}>
                  <ReportsPage />
                </RequireRole>
              } />
              <Route path="/confirm-grn" element={
                <RequireRole role={["SYSTEM_ADMINISTRATOR", "CENTRAL_STORE_MANAGER"]}>
                  <ConfirmGRNPage />
                </RequireRole>
              } />
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
