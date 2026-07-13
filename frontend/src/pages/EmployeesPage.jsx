import { useNavigate } from "react-router-dom";
import { UserPlus2 } from "lucide-react";
import CardHeader from "../components/CardHeader";
import Badge from "../components/Badge";
import { useAppData } from "../context/AppDataContext";

// There is no separate "Employee" entity in the backend — assignable people
// are AppUser records (see UserController/AppUser.java), and a person is
// attached to a project via POST /api/projects/{id}/employees/{employeeId}
// (see ProjectController.assignEmployee). This page used to keep its own
// fake, disconnected employee list; it now reads the real user directory
// and points project-assignment to the project it actually belongs to.
const ROLE_LABEL = {
  CENTRAL_STORE_MANAGER: "Central Store Manager",
  SITE_STORE_MANAGER: "Site Store Manager",
  PROCUREMENT_OFFICER: "Procurement Officer",
  FINANCE: "Finance",
  EXECUTIVE_MANAGEMENT: "Executive Management",
  SYSTEM_ADMINISTRATOR: "System Administrator",
};

export default function EmployeesPage() {
  const navigate = useNavigate();
  const { users } = useAppData();

  return (
    <div className="page">
      <div className="card">
        <CardHeader
          title="Employees"
          subtitle="Every user in the system is a potential project assignee — create new ones from Users."
          actions={[
            {
              label: "Manage Users",
              icon: <UserPlus2 size={16} />,
              variant: "primary",
              onClick: () => navigate("/users"),
            },
          ]}
        />

        <table className="table">
          <thead>
            <tr>
              <th>Name</th>
              <th>Store</th>
              <th>Roles</th>
              <th>Status</th>
            </tr>
          </thead>
          <tbody>
            {users.map((u) => (
              <tr key={u.id}>
                <td>{u.fullName}</td>
                <td>{u.store}</td>
                <td>
                  {u.roles.length ? (
                    u.roles.map((role) => (
                      <Badge key={role} type={role === "CENTRAL_STORE_MANAGER" ? "success" : "info"}>
                        {ROLE_LABEL[role] || role}
                      </Badge>
                    ))
                  ) : (
                    <span style={{ color: "#64748b", fontSize: 13 }}>No roles</span>
                  )}
                </td>
                <td>
                  <Badge type={!u.active ? "danger" : u.locked ? "warning" : "success"}>
                    {!u.active ? "Deactivated" : u.locked ? "Locked" : "Active"}
                  </Badge>
                </td>
              </tr>
            ))}
            {users.length === 0 && (
              <tr>
                <td colSpan={4} style={{ textAlign: "center", color: "#64748b" }}>
                  No users found.
                </td>
              </tr>
            )}
          </tbody>
        </table>
        <p style={{ color: "#64748b", fontSize: 13, marginTop: 16 }}>
          To assign someone to a specific project, open that project's page and use "Assign Employee" there.
        </p>
      </div>
    </div>
  );
}
