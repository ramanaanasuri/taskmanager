import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter, Routes, Route } from 'react-router-dom';
import './App.css';
import App from './App';
import OAuth2Redirect from './OAuth2Redirect';

const root = ReactDOM.createRoot(document.getElementById('root'));
root.render(
  <React.StrictMode>
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<App />} />
        <Route path="/oauth2/redirect" element={<OAuth2Redirect />} />
      </Routes>
    </BrowserRouter>
  </React.StrictMode>
);