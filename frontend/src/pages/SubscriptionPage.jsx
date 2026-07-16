import { useState, useEffect } from "react";
import { Settings, Plus, Minus, Server, Activity } from "lucide-react";
import CardHeader from "../components/CardHeader";
import { apiFetch } from "../api";
import { useAppData } from "../context/AppDataContext";
import { useAppModal } from "../context/ModalContext";

export default function SubscriptionPage() {
  const { user } = useAppData();
  const { showAlert } = useAppModal();
  const [sub, setSub] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [isUpdating, setIsUpdating] = useState(false);
  const [newSlots, setNewSlots] = useState(3);

  async function loadSubscription() {
    try {
      const data = await apiFetch("/api/subscription");
      setSub(data);
      setNewSlots(data.allowedStoreSlots);
    } catch (e) {
      setError(e.message || "Failed to load subscription settings");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    loadSubscription();
  }, []);

  async function updateSlots(e) {
    e.preventDefault();
    setError(null);
    setIsUpdating(true);
    try {
      const data = await apiFetch("/api/subscription", {
        method: "PUT",
        body: { allowedStoreSlots: newSlots }
      });
      setSub(data);
      showAlert({ title: "Success", message: "Subscription updated successfully!", type: "success" });
    } catch (err) {
      setError(err.message || "Failed to update subscription slots");
    } finally {
      setIsUpdating(false);
    }
  }

  if (loading) return <div className="page" style={{ padding: 20 }}>Loading subscription data...</div>;

  const usagePercent = sub ? Math.round((sub.operationalCount / sub.allowedStoreSlots) * 100) : 0;
  
  return (
    <div className="page">
      <div className="card" style={{ maxWidth: 600, margin: '0 auto' }}>
        <CardHeader 
          title="Company Subscription Settings" 
          icon={<Settings size={20} />} 
        />
        
        {error && (
          <div style={{ padding: '12px 16px', background: '#fee2e2', color: '#b91c1c', borderRadius: 6, marginBottom: 20 }}>
            {error}
          </div>
        )}

        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 20, marginBottom: 32 }}>
          <div style={{ background: '#f8fafc', padding: 20, borderRadius: 12, border: '1px solid #e2e8f0', textAlign: 'center' }}>
            <Activity size={24} style={{ color: '#2563eb', marginBottom: 8 }} />
            <div style={{ fontSize: '2em', fontWeight: 700, color: '#1e293b' }}>{sub?.operationalCount || 0}</div>
            <div style={{ color: '#64748b', fontSize: '0.9em', fontWeight: 500 }}>Active Operational Stores</div>
          </div>
          
          <div style={{ background: '#f8fafc', padding: 20, borderRadius: 12, border: '1px solid #e2e8f0', textAlign: 'center' }}>
            <Server size={24} style={{ color: '#10b981', marginBottom: 8 }} />
            <div style={{ fontSize: '2em', fontWeight: 700, color: '#1e293b' }}>{sub?.allowedStoreSlots || 0}</div>
            <div style={{ color: '#64748b', fontSize: '0.9em', fontWeight: 500 }}>Allowed Store Slots</div>
          </div>
        </div>

        <div style={{ marginBottom: 32 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 8 }}>
            <span style={{ fontWeight: 600, color: '#475569' }}>Capacity Usage</span>
            <span style={{ fontWeight: 600, color: usagePercent > 90 ? '#ef4444' : '#2563eb' }}>{usagePercent}%</span>
          </div>
          <div style={{ height: 12, background: '#e2e8f0', borderRadius: 6, overflow: 'hidden' }}>
            <div style={{ 
              height: '100%', 
              background: usagePercent > 90 ? '#ef4444' : usagePercent > 75 ? '#f59e0b' : '#2563eb', 
              width: `${Math.min(usagePercent, 100)}%`,
              transition: 'width 0.5s ease-out'
            }} />
          </div>
          <p style={{ marginTop: 8, fontSize: '0.85em', color: '#64748b' }}>
            Stores in the "Closing" (Pending Shutdown) state do not consume operational slots.
          </p>
        </div>

        <form onSubmit={updateSlots} style={{ borderTop: '1px solid #e2e8f0', paddingTop: 24 }}>
          <h3 style={{ margin: '0 0 16px 0', fontSize: '1.1em', color: '#1e293b' }}>Manage Subscription</h3>
          
          <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
            <div style={{ flex: 1 }}>
              <label style={{ display: 'block', marginBottom: 8, fontWeight: 500, color: '#475569' }}>
                Purchase/Update Allowed Slots
              </label>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <button 
                  type="button"
                  className="btn btn-outline" 
                  style={{ padding: '8px 12px' }}
                  onClick={() => setNewSlots(Math.max(sub?.operationalCount || 1, newSlots - 1))}
                  disabled={newSlots <= (sub?.operationalCount || 1)}
                >
                  <Minus size={16} />
                </button>
                <input 
                  type="number" 
                  className="input" 
                  value={newSlots} 
                  onChange={e => setNewSlots(parseInt(e.target.value) || 1)}
                  style={{ width: 80, textAlign: 'center', fontSize: '1.1em', fontWeight: 600 }}
                  min={sub?.operationalCount || 1}
                />
                <button 
                  type="button"
                  className="btn btn-outline" 
                  style={{ padding: '8px 12px' }}
                  onClick={() => setNewSlots(newSlots + 1)}
                >
                  <Plus size={16} />
                </button>
              </div>
            </div>
            
            <div style={{ paddingTop: 28 }}>
              <button 
                type="submit" 
                className="btn btn-primary" 
                disabled={isUpdating || newSlots === sub?.allowedStoreSlots}
                style={{ height: 42, padding: '0 24px' }}
              >
                {isUpdating ? "Saving..." : "Update Limits"}
              </button>
            </div>
          </div>
        </form>
      </div>
    </div>
  );
}
