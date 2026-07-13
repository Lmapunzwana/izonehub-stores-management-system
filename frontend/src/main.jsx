import React from 'react'
import ReactDOM from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import App from './App'
import { AppDataProvider } from './context/AppDataContext'
import { ModalProvider } from './context/ModalContext'
import './styles.css'

ReactDOM.createRoot(document.getElementById('root')).render(
  <BrowserRouter>
    <ModalProvider>
      <AppDataProvider>
        <App />
      </AppDataProvider>
    </ModalProvider>
  </BrowserRouter>
)
