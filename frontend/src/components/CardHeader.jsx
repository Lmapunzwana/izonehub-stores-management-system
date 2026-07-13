import { Plus } from "lucide-react";

/**
 * CardHeader
 * A flexible dark card-header that covers every variant seen across the app:
 *
 *  - title only                              ("Item Information")
 *  - title + inline count badge + 2 actions  ("Items · 1,248 items" + Add Item / Export)
 *  - title + single primary action           ("Expected Receipts" + New Expected Receipt)
 *  - title + status pill (no action)         ("Confirm Goods Received Note" + Awaiting GRN)
 *  - icon + title + subtitle + status pill   ("Add Item to Material Request" + Stock validated)
 *  - icon + title + primary action           ("Assigned Employees" + Assign Employee)
 *
 * Every piece (icon, badge, subtitle, status, actions) is optional, so the
 * same component renders any combination of the above without extra props
 * juggling at the call site.
 *
 * Props
 * ------
 * icon        : ReactNode                 – small icon rendered before the title
 * title       : string  (required)
 * badge       : string                    – inline muted pill next to the title, e.g. "1,248 items"
 * subtitle    : string | string[]         – shown under the title; arrays are joined with " · "
 * status      : { label, variant }        – pill on the right (variant: default | success | warning | danger | info)
 * actions     : { label, icon, variant, onClick }[]
 *                                          – buttons on the right (variant: primary | outline | ghost)
 * right       : ReactNode                 – escape hatch, overrides status/actions entirely
 * dark        : boolean                  – opt-in dark card-header variant (default is light)
 * className   : string                    – extra class(es) on the outer wrapper
 *
 * Examples
 * --------
 * <CardHeader title="Item Information" />
 *
 * <CardHeader
 *   title="Items"
 *   badge="1,248 items"
 *   actions={[
 *     { label: "Add Item", icon: <Plus size={16} />, variant: "primary", onClick: onAdd },
 *     { label: "Export", icon: <Download size={16} />, variant: "outline", onClick: onExport },
 *   ]}
 * />
 *
 * <CardHeader title="Expected Receipts"
 *   actions={[{ label: "New Expected Receipt", icon: <Plus size={16} />, variant: "primary" }]} />
 *
 * <CardHeader title="Confirm Goods Received Note"
 *   status={{ label: "Awaiting GRN", variant: "warning" }} />
 *
 * <CardHeader
 *   icon={<CirclePlus size={20} />}
 *   title="Add Item to Material Request"
 *   subtitle={["Request No: MR-00921", "Project: Rural Clinic Electrification"]}
 *   status={{ label: "Stock validated", variant: "success" }}
 * />
 *
 * <CardHeader icon={<Users size={20} />} title="Assigned Employees"
 *   actions={[{ label: "Assign Employee", icon: <UserPlus size={16} />, variant: "primary" }]} />
 */
export default function CardHeader({
  icon,
  title,
  badge,
  subtitle,
  status,
  actions,
  right,
  dark = false,
  className = "",
}) {
  const subtitleText = Array.isArray(subtitle)
    ? subtitle.join(" · ")
    : subtitle;

  return (
    <div className={`ch-header ${dark ? "ch-header--dark" : ""} ${className}`}>
      <div className="ch-header__left">
        <div className="ch-header__title-row">
          {icon && <span className="ch-header__icon">{icon}</span>}
          <h2 className="ch-header__title">{title}</h2>
          {badge && <span className="ch-chip">{badge}</span>}
        </div>
        {subtitleText && <p className="ch-header__subtitle">{subtitleText}</p>}
      </div>

      <div className="ch-header__right">
        {right ? (
          right
        ) : (
          <div style={{ display: "flex", gap: "12px", alignItems: "center" }}>
            {status && (
              <span
                className={`ch-status ch-status--${status.variant || "default"}`}
              >
                {status.label}
              </span>
            )}
            {actions?.length ? (
              actions.map((action, i) => (
                <button
                  key={i}
                  type="button"
                  onClick={action.onClick}
                  className={`ch-btn ch-btn--${action.variant || "outline"}`}
                >
                  {action.icon}
                  {action.label}
                </button>
              ))
            ) : null}
          </div>
        )}
      </div>
    </div>
  );
}
