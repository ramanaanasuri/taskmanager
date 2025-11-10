import React, { useState, useEffect } from 'react';
import axios from 'axios';
import API_BASE_URL from './config';
import './App.css';

// ============ WEB PUSH: VAPID Key & Helper ============
const VAPID_PUBLIC_KEY = process.env.REACT_APP_VAPID_PUBLIC_KEY;

const urlBase64ToUint8Array = (base64String) => {
  const pad = '='.repeat((4 - base64String.length % 4) % 4);
  const b64 = (base64String + pad).replace(/-/g, '+').replace(/_/g, '/');
  const raw = atob(b64);
  return Uint8Array.from([...raw].map(c => c.charCodeAt(0)));
};

const formatDateForBackend = (dateString) => {
  if (!dateString) return null;
  return dateString.includes('T') ? dateString + ':00' : dateString;
};

function App() {
  const [tasks, setTasks] = useState([]);
  const [newTask, setNewTask] = useState('');
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);
  const [authToken, setAuthToken] = useState(null);
  const [newTaskPriority, setNewTaskPriority] = useState('MEDIUM');
  const [newTaskDueDate, setNewTaskDueDate] = useState('');
  const [tempDueDate, setTempDueDate] = useState('');
  const [editingTask, setEditingTask] = useState(null);
  const [tempEditDate, setTempEditDate] = useState('');
  
  // ============ WEB PUSH: New State Variables ============
  const [enableNotifications, setEnableNotifications] = useState(false);
  const [notificationPermission, setNotificationPermission] = useState('default');

  // ============ DEBUG: Component Mount ============
  useEffect(() => {
    console.log('=== 🚀 App Component Mounted ===');
    console.log('🔧 API_BASE_URL:', API_BASE_URL);
    console.log('📍 Current URL:', window.location.href);
    console.log('🔗 URL Search Params:', window.location.search);
  }, []);

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const tokenFromUrl = params.get('token');

    if (tokenFromUrl) {
      console.log('🎫 Token received on "/" via query param, storing it.');
      try {
        localStorage.setItem('jwt_token', tokenFromUrl);
      } catch (e) {
        console.warn('Could not write token to localStorage:', e);
      }
      setAuthToken(tokenFromUrl);
      window.history.replaceState({}, document.title, window.location.pathname);
    } else {
      const existing = localStorage.getItem('jwt_token');
      if (existing) {
        console.log('🎫 Token loaded from localStorage.');
        setAuthToken(existing);
      } else {
        console.log('❌ No token found in URL or localStorage.');
        setLoading(false);
      }
    }
  }, []);

  useEffect(() => {
    if (!authToken) return;
    checkAuth(authToken);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [authToken]);

  // ============ WEB PUSH: Register Service Worker ============
  useEffect(() => {
    if ('serviceWorker' in navigator && 'PushManager' in window) {
      navigator.serviceWorker.register('/sw.js')
        .then(reg => console.log('✅ Service Worker registered:', reg))
        .catch(err => console.error('❌ SW registration failed:', err));
      
      setNotificationPermission(Notification.permission);
    } else {
      console.warn('⚠️ Push notifications not supported on this browser');
    }
  }, []);

  // ============ WEB PUSH: Auto-enable after first task ============
  useEffect(() => {
    if (tasks.length >= 1 && notificationPermission === 'granted') {
      setEnableNotifications(true);
    }
  }, [tasks.length, notificationPermission]);

  const checkAuth = async (token) => {
    console.log('\n🔐 === Starting Authentication Check ===');
    console.log('🎫 Using token:', token ? `${token.substring(0, 30)}...` : '❌ NONE');

    if (!token) {
      console.log('⚠️ No token available - user needs to login');
      setLoading(false);
      return;
    }

    try {
      const { data, status } = await axios.get(`${API_BASE_URL}/api/auth/me`, {
        headers: { Authorization: `Bearer ${token}` }
      });

      console.log('✅ Auth Success!');
      setUser(data);
      fetchTasks(token);
    } catch (error) {
      console.error('\n❌ === Auth Failed ===');
      console.error('💥 Error Message:', error.message);

      try {
        localStorage.removeItem('jwt_token');
        localStorage.removeItem('user');
      } catch (_) {}
      setUser(null);
      setAuthToken(null);
      setLoading(false);
    }
  };

  const fetchTasks = async (tokenParam) => {
    console.log('\n📋 === Fetching Tasks ===');
    const token = tokenParam || localStorage.getItem('jwt_token');

    try {
      const response = await axios.get(`${API_BASE_URL}/api/tasks`, {
        headers: { Authorization: `Bearer ${token}` }
      });

      console.log('✅ Tasks fetched successfully');
      setTasks(response.data);
    } catch (error) {
      console.error('❌ Error fetching tasks:', error.message);
    } finally {
      setLoading(false);
    }
  };

  // ============ WEB PUSH: Subscribe to Push Notifications ============
  const subscribeToPush = async () => {
    if (!('serviceWorker' in navigator) || !('PushManager' in window)) {
      alert('Push notifications not supported on this browser');
      return false;
    }

    if (!VAPID_PUBLIC_KEY) {
      console.error('❌ VAPID public key not configured');
      alert('Push notifications not configured. Please contact support.');
      return false;
    }

    try {
      const reg = await navigator.serviceWorker.ready;
      const permission = await Notification.requestPermission();
      
      if (permission !== 'granted') {
        console.log('⚠️ Notification permission denied');
        alert('Notification permission denied. You can enable it later in browser settings.');
        return false;
      }

      setNotificationPermission('granted');

      const subscription = await reg.pushManager.subscribe({
        userVisibleOnly: true,
        applicationServerKey: urlBase64ToUint8Array(VAPID_PUBLIC_KEY)
      });

      console.log('📱 Push subscription created:', subscription);

      // Send subscription to backend
      const token = authToken || localStorage.getItem('jwt_token');
      await axios.post(
        `${API_BASE_URL}/api/push/subscribe`,
        subscription,
        { headers: { Authorization: `Bearer ${token}` } }
      );

      console.log('✅ Push notifications enabled successfully');
      return true;
    } catch (error) {
      console.error('❌ Failed to subscribe to push notifications:', error);
      alert('Failed to enable notifications. Please try again.');
      return false;
    }
  };

  const addTask = async (e) => {
    e.preventDefault();
    console.log('\n➕ === Adding New Task ===');
  
    if (!newTask.trim()) {
      console.log('⚠️ Task title is empty - aborting');
      return;
    }
  
    const token = authToken || localStorage.getItem('jwt_token');
    const formattedDueDate = newTaskDueDate ? newTaskDueDate + ':00' : null;
  
    // ============ WEB PUSH: Subscribe if notifications enabled ============
    if (enableNotifications && notificationPermission !== 'granted') {
      const subscribed = await subscribeToPush();
      if (!subscribed) {
        console.log('⚠️ Notifications not enabled, continuing without them');
      }
    }

    try {
      const response = await axios.post(
        `${API_BASE_URL}/api/tasks`,
        { 
          title: newTask, 
          completed: false,
          priority: newTaskPriority,
          dueDate: formattedDueDate,
          notificationsEnabled: enableNotifications  // ✅ WEB PUSH: Send checkbox state
        },
        { headers: { Authorization: `Bearer ${token}` } }
      );
  
      console.log('✅ Task added successfully');
      setTasks([...tasks, response.data]);
      setNewTask('');
      setNewTaskPriority('MEDIUM');
      setNewTaskDueDate('');
      setTempDueDate('');
      // Keep enableNotifications checked for next task
    } catch (error) {
      console.error('❌ Error adding task:', error.message);
      alert('Failed to add task. Please try again.');
    }
  };

  const updateTask = async (id, updates) => {
    console.log('\n✏️ === Updating Task ===');

    const token = authToken || localStorage.getItem('jwt_token');

    try {
      const task = tasks.find(t => t.id === id);
      
      let formattedDueDate = updates.dueDate;
      if (formattedDueDate && typeof formattedDueDate === 'string' && formattedDueDate.length === 16) {
        formattedDueDate = formattedDueDate + ':00';
      }

      const response = await axios.put(
        `${API_BASE_URL}/api/tasks/${id}`,
        { 
          ...task, 
          ...updates,
          dueDate: formattedDueDate
        },
        { headers: { Authorization: `Bearer ${token}` } }
      );

      console.log('✅ Task updated successfully');
      setTasks(tasks.map(t => (t.id === id ? response.data : t)));
      setEditingTask(null);
      setTempEditDate('');
    } catch (error) {
      console.error('❌ Error updating task:', error.message);
    }
  };

  const toggleTask = async (id) => {
    console.log('\n🔄 === Toggling Task ===');

    const token = authToken || localStorage.getItem('jwt_token');

    try {
      const task = tasks.find(t => t.id === id);
      const response = await axios.put(
        `${API_BASE_URL}/api/tasks/${id}`,
        { ...task, completed: !task.completed },
        { headers: { Authorization: `Bearer ${token}` } }
      );

      console.log('✅ Task toggled successfully');
      setTasks(tasks.map(t => (t.id === id ? response.data : t)));
    } catch (error) {
      console.error('❌ Error updating task:', error.message);
    }
  };

  const deleteTask = async (id) => {
    console.log('\n🗑️ === Deleting Task ===');

    const token = authToken || localStorage.getItem('jwt_token');

    try {
      await axios.delete(`${API_BASE_URL}/api/tasks/${id}`, {
        headers: { Authorization: `Bearer ${token}` }
      });

      console.log('✅ Task deleted successfully');
      setTasks(tasks.filter(t => t.id !== id));
    } catch (error) {
      console.error('❌ Error deleting task:', error.message);
    }
  };

  const handleLogin = () => {
    window.location.href = `${API_BASE_URL}/oauth2/authorization/google`;
  };

  const handleFacebookLogin = () => {
    window.location.href = `${API_BASE_URL}/oauth2/authorization/facebook`;
  };

  const handleLogout = () => {
    try {
      localStorage.removeItem('jwt_token');
      localStorage.removeItem('user');
    } catch (_) {}
    setUser(null);
    setTasks([]);
    setAuthToken(null);
  };

  const handleConfirmDate = () => {
    if (tempDueDate) {
      setNewTaskDueDate(tempDueDate);
    }
  };

  const handleClearDate = () => {
    setTempDueDate('');
    setNewTaskDueDate('');
  };

  const handleConfirmEditDate = () => {
    if (tempEditDate) {
      setEditingTask({...editingTask, dueDate: tempEditDate});
    }
  };

  const handleClearEditDate = () => {
    setTempEditDate('');
    setEditingTask({...editingTask, dueDate: null});
  };

  const formatDisplayDate = (dateString) => {
    if (!dateString) return 'Not set';
    try {
      const date = new Date(dateString);
      return date.toLocaleString('en-US', {
        month: 'short',
        day: 'numeric',
        year: 'numeric',
        hour: 'numeric',
        minute: '2-digit',
        hour12: true
      });
    } catch (e) {
      return dateString;
    }
  };

  if (loading) {
    return (
      <div className="loading-container">
        <div className="loading-spinner"></div>
        <p>Loading Task Manager...</p>
      </div>
    );
  }

  if (!user) {
    return (
      <div className="login-container">
        <div className="login-card">
          <div className="login-header">
            <div className="logo-container">
              <span className="logo-icon">📋</span>
              <h1 className="app-title">TASK MANAGER PRO</h1>
            </div>
            <p className="tagline">Your Day, Organized.</p>
            <p className="subtitle">A smarter way to plan and complete what matters most.</p>
          </div>

          <div className="login-buttons">
            <button onClick={handleLogin} className="google-login-btn">
              <span className="google-icon">G</span>
              <span>Continue with Google</span>
            </button>
            <button onClick={handleFacebookLogin} className="facebook-login-btn">
              <span className="facebook-icon">f</span>
              <span>Continue with Facebook</span>
            </button>
          </div>

          <p className="login-footer">
            Secure sign-in with OAuth 2.0
          </p>
        </div>
      </div>
    );
  }

  return (
    <div className="app-container">
      {/* Header */}
      <header className="app-header">
        <div className="header-content">
          <div className="header-left">
            <span className="header-logo">📋</span>
            <h1 className="header-title">TASK MANAGER PRO</h1>
          </div>
          <div className="header-right">
            <span className="user-greeting">Welcome, {user.name || user.email}</span>
            <button onClick={handleLogout} className="logout-btn">
              Sign Out
            </button>
          </div>
        </div>
      </header>

      {/* Main Content */}
      <main className="main-content">
        {/* Hero Section */}
        <div className="hero-section">
          <h1 className="hero-title">Your Day, Organized.</h1>
          <p className="hero-subtitle">A smarter way to plan and complete what matters most.</p>
        </div>

        {/* Task Creation Form */}
        <div className="form-container">
          <h2 className="form-title">Create New Task</h2>
          <p className="form-subtitle">Add a task to get started</p>

          <form onSubmit={addTask} className="task-form">
            <div className="form-group">
              <label htmlFor="task-name" className="form-label">
                Task Name <span className="required">*</span>
              </label>
              <input
                id="task-name"
                type="text"
                className="form-input"
                placeholder="Enter task name..."
                value={newTask}
                onChange={(e) => setNewTask(e.target.value)}
                required
              />
            </div>

            <div className="form-row">
              <div className="form-group half">
                <label htmlFor="priority" className="form-label">Priority</label>
                <select
                  id="priority"
                  className="form-select"
                  value={newTaskPriority}
                  onChange={(e) => setNewTaskPriority(e.target.value)}
                >
                  <option value="LOW">Low</option>
                  <option value="MEDIUM">Medium</option>
                  <option value="HIGH">High</option>
                </select>
              </div>

              <div className="form-group half">
                <label htmlFor="scheduled-date" className="form-label">Scheduled Date</label>
                <div className="date-input-container">
                  <input
                    id="scheduled-date"
                    type="datetime-local"
                    className="form-input date-input"
                    value={tempDueDate}
                    onChange={(e) => setTempDueDate(e.target.value)}
                  />
                  {tempDueDate && !newTaskDueDate && (
                    <button
                      type="button"
                      onClick={handleConfirmDate}
                      className="date-confirm-btn"
                      title="Confirm date"
                    >
                      ✓
                    </button>
                  )}
                  {newTaskDueDate && (
                    <button
                      type="button"
                      onClick={handleClearDate}
                      className="date-clear-btn"
                      title="Clear date"
                    >
                      ✕
                    </button>
                  )}
                </div>
                {newTaskDueDate && (
                  <small className="date-preview">Selected: {formatDisplayDate(newTaskDueDate)}</small>
                )}
              </div>
            </div>

            {/* ============ WEB PUSH: Notification Checkbox ============ */}
            {tasks.length >= 0 && (
              <div className="notification-option">
                <label className="notification-label">
                  <input
                    type="checkbox"
                    checked={enableNotifications}
                    onChange={(e) => setEnableNotifications(e.target.checked)}
                    className="notification-checkbox"
                  />
                  <span className="notification-text">🔔 Text me reminders for this task</span>
                </label>
                {enableNotifications && notificationPermission === 'default' && (
                  <small className="notification-hint">
                    📱 You'll be asked for notification permission when you add the task
                  </small>
                )}
                {enableNotifications && notificationPermission === 'denied' && (
                  <small className="notification-warning">
                    ⚠️ Notifications are blocked. Please enable them in your browser settings.
                  </small>
                )}
              </div>
            )}

            <button type="submit" className="add-btn">
              <span className="btn-icon">+</span> Add Task
            </button>
          </form>
        </div>

        {/* Task List */}
        <div className="tasks-container">
          <div className="tasks-header">
            <h2 className="tasks-title">My Tasks ({tasks.length})</h2>
          </div>

          {tasks.length === 0 ? (
            <div className="no-tasks">
              <p>📋 No tasks yet.</p>
              <p className="no-tasks-subtitle">Create your first task above to get started!</p>
            </div>
          ) : (
            <div className="task-table">
              <div className="task-table-header">
                <div className="th-status">Status</div>
                <div className="th-name">Task Name</div>
                <div className="th-priority">Priority</div>
                <div className="th-scheduled">Scheduled Date</div>
                <div className="th-actions">Actions</div>
              </div>

              <div className="task-table-body">
                {tasks.map(task => (
                  <div key={task.id} className={`task-row ${task.completed ? 'completed-task' : ''}`}>
                    {/* Main Info Row: Checkbox + Task Name */}
                    <div className="task-main-info">
                      <div className="td-status">
                        <input
                          type="checkbox"
                          checked={task.completed}
                          onChange={() => toggleTask(task.id)}
                          className="task-checkbox"
                          title={task.completed ? 'Mark as incomplete' : 'Mark as complete'}
                        />
                      </div>

                      <div className="td-name">
                        <span className={`task-title ${task.completed ? 'completed' : ''}`}>
                          {task.title}
                          {task.notificationsEnabled && (
                            <span className="notification-badge" title="Notifications enabled">🔔</span>
                          )}
                        </span>
                      </div>
                    </div>

                    {/* Metadata Row: Priority + Scheduled (compact on one line) */}
                    <div className="task-metadata">
                      <div className="td-priority">
                        <span className={`priority-badge priority-${task.priority.toLowerCase()}`}>
                          {task.priority}
                        </span>
                      </div>

                      <div className="td-scheduled">
                        {task.dueDate ? (
                          <div className="scheduled-info">
                            <span className="scheduled-icon">📅</span>
                            <span className="scheduled-date">{formatDisplayDate(task.dueDate)}</span>
                          </div>
                        ) : (
                          <span className="no-date">Not scheduled</span>
                        )}
                      </div>
                    </div>

                    {/* Actions Row */}
                    <div className="td-actions">
                      <button
                        onClick={() => {
                          setEditingTask(task);
                          setTempEditDate(task.dueDate ? new Date(task.dueDate).toISOString().slice(0, 16) : '');
                        }}
                        className="edit-btn"
                        title="Edit task"
                      >
                        ✏️
                      </button>
                      <button
                        onClick={() => deleteTask(task.id)}
                        className="delete-btn"
                        title="Delete task"
                      >
                        🗑️
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      </main>

      {/* Edit Modal */}
      {editingTask && (
        <div className="modal-overlay" onClick={() => {
          setEditingTask(null);
          setTempEditDate('');
        }}>
          <div className="modal-content" onClick={(e) => e.stopPropagation()}>
            <h2 className="modal-title">Edit Task</h2>
            <form onSubmit={(e) => {
              e.preventDefault();
              updateTask(editingTask.id, {
                title: editingTask.title,
                priority: editingTask.priority,
                dueDate: editingTask.dueDate
              });
            }}>
              <div className="modal-form-group">
                <label htmlFor="edit-task-name" className="modal-label">Task Name *</label>
                <input
                  id="edit-task-name"
                  type="text"
                  value={editingTask.title}
                  onChange={(e) => setEditingTask({...editingTask, title: e.target.value})}
                  placeholder="Task name"
                  className="modal-input"
                  required
                />
              </div>

              <div className="modal-form-group">
                <label htmlFor="edit-task-priority" className="modal-label">Priority</label>
                <select
                  id="edit-task-priority"
                  value={editingTask.priority}
                  onChange={(e) => setEditingTask({...editingTask, priority: e.target.value})}
                  className="modal-input"
                >
                  <option value="LOW">Low</option>
                  <option value="MEDIUM">Medium</option>
                  <option value="HIGH">High</option>
                </select>
              </div>

              <div className="modal-form-group">
                <label htmlFor="edit-task-due-date" className="modal-label">Scheduled Date</label>
                <div className="date-input-group">
                  <input
                    id="edit-task-due-date"
                    type="datetime-local"
                    value={tempEditDate}
                    onChange={(e) => setTempEditDate(e.target.value)}
                    className="modal-input"
                  />
                  <button
                    type="button"
                    onClick={handleConfirmEditDate}
                    className="date-confirm-btn"
                    disabled={!tempEditDate}
                    title="Apply selected date"
                  >
                    ✓
                  </button>
                  {editingTask.dueDate && (
                    <button
                      type="button"
                      onClick={handleClearEditDate}
                      className="date-clear-btn"
                      title="Clear date"
                    >
                      ✕
                    </button>
                  )}
                </div>
                {editingTask.dueDate && (
                  <small className="date-preview">Selected: {formatDisplayDate(editingTask.dueDate)}</small>
                )}
              </div>

              <div className="modal-actions">
                <button type="submit" className="modal-btn-save">
                  💾 Save Changes
                </button>
                <button type="button" onClick={() => {
                  setEditingTask(null);
                  setTempEditDate('');
                }} className="modal-btn-cancel">
                  ✖ Cancel
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
      {/* Footer */}
      <footer className="app-footer">
        <div className="footer-content">
          <div className="footer-powered">
            <div>
              Powered by <span className="footer-company">SriInfosoft Inc<sup className="footer-trademark">®</sup></span>
            </div>
            <div className="footer-copyright">
              © {new Date().getFullYear()} All rights reserved
            </div>
          </div>
        </div>
      </footer>
    </div>
  );
}

export default App;
