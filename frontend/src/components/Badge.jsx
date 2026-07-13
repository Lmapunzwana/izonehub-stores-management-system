export default function Badge({ children, type = 'default' }) {
  return <span className={`badge ${type}`}>{children}</span>
}
