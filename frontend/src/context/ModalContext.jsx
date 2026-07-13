import React, { createContext, useContext, useState, useCallback } from "react";
import { AlertCircle, CheckCircle2, Info, TriangleAlert } from "lucide-react";

const ModalContext = createContext(null);

export function ModalProvider({ children }) {
  const [modalState, setModalState] = useState({
    isOpen: false,
    title: "",
    message: "",
    type: "info", // "info" | "warning" | "danger" | "success"
    isConfirm: false,
    confirmText: "OK",
    cancelText: "Cancel",
    onConfirm: null,
    onCancel: null,
  });

  const hideModal = useCallback(() => {
    setModalState((prev) => ({ ...prev, isOpen: false }));
  }, []);

  const showAlert = useCallback(({ title, message, type = "info" }) => {
    setModalState({
      isOpen: true,
      title: title || "Alert",
      message,
      type,
      isConfirm: false,
      confirmText: "OK",
      cancelText: "",
      onConfirm: hideModal,
      onCancel: hideModal,
    });
  }, [hideModal]);

  const showConfirm = useCallback(({ title, message, type = "warning", confirmText = "Confirm", cancelText = "Cancel", onConfirm }) => {
    setModalState({
      isOpen: true,
      title: title || "Confirm Action",
      message,
      type,
      isConfirm: true,
      confirmText,
      cancelText,
      onConfirm: () => {
        hideModal();
        if (onConfirm) onConfirm();
      },
      onCancel: hideModal,
    });
  }, [hideModal]);

  const ICONS = {
    info: <Info size={24} color="#3b82f6" />,
    success: <CheckCircle2 size={24} color="#16a34a" />,
    warning: <TriangleAlert size={24} color="#ea580c" />,
    danger: <AlertCircle size={24} color="#dc2626" />,
  };

  return (
    <ModalContext.Provider value={{ showAlert, showConfirm }}>
      {children}
      {modalState.isOpen && (
        <div className="app-modal-backdrop">
          <div className="app-modal">
            <div className={`app-modal-icon app-modal-icon--${modalState.type}`}>
              {ICONS[modalState.type]}
            </div>
            <h2 className="app-modal-title">{modalState.title}</h2>
            <p className="app-modal-message">{modalState.message}</p>
            <div className="app-modal-actions">
              {modalState.isConfirm && (
                <button className="ch-btn ch-btn--outline" onClick={modalState.onCancel}>
                  {modalState.cancelText}
                </button>
              )}
              <button
                className={`ch-btn ch-btn--${modalState.type === "danger" ? "danger" : "primary"}`}
                onClick={modalState.onConfirm}
              >
                {modalState.confirmText}
              </button>
            </div>
          </div>
        </div>
      )}
    </ModalContext.Provider>
  );
}

export function useAppModal() {
  const ctx = useContext(ModalContext);
  if (!ctx) throw new Error("useAppModal must be used within ModalProvider");
  return ctx;
}
