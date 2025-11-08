import React, { useState, useEffect } from 'react';
import axios from 'axios';
import API_BASE_URL from './config';
import './App.css';

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

  const addTask = async (e) => {
    e.preventDefault();
    console.log('\n➕ === Adding New Task ===');
  
    if (!newTask.trim()) {
      console.log('⚠️ Task title is empty - aborting');
      return;
    }
  
    const token = authToken || localStorage.getItem('jwt_token');
    const formattedDueDate = newTaskDueDate ? newTaskDueDate + ':00' : null;
  
    try {
      const response = await axios.post(
        `${API_BASE_URL}/api/tasks`,
        { 
          title: newTask, 
          completed: false,
          priority: newTaskPriority,
          dueDate: formattedDueDate
        },
        { headers: { Authorization: `Bearer ${token}` } }
      );
  
      console.log('✅ Task added successfully');
      setTasks([...tasks, response.data]);
      setNewTask('');
      setNewTaskPriority('MEDIUM');
      setNewTaskDueDate('');
      setTempDueDate('');
    } catch (error) {
      console.error('❌ Error adding task:', error.message);
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
    const date = new Date(dateString);
    const options = { 
      year: 'numeric', 
      month: 'short', 
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    };
    return date.toLocaleDateString('en-US', options);
  };

  if (loading) {
    return <div className="loading">Loading...</div>;
  }

  if (!user) {
    const params = new URLSearchParams(window.location.search);
    const loginError = params.get('loginError');

    return (
      <div className="login-container">
        <div className="login-box">
          <h1>Task Manager</h1>
          <p>Manage your tasks efficiently</p>
          {loginError && (
            <div style={{ color: '#ffebee', background: '#c62828', padding: '8px 12px', borderRadius: 6, marginBottom: 12 }}>
              Login failed. Please try again.
            </div>
          )}
          <button onClick={handleLogin} className="google-btn">
            Sign in with Google
          </button>
          <button onClick={handleFacebookLogin} className="facebook-btn">
            Sign in with Facebook (Coming Soon)
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="app">
      <header className="app-header">
        <div className="header-banner">
          <div className="header-content">
            <div className="header-branding">
              {/* OPTION 1: Use your uploaded clipboard image */}
              {/* Briefcase icon */}
              <div className="header-logo">
                <svg viewBox="0 0 48 48" fill="none" className="logo-svg" xmlns="http://www.w3.org/2000/svg">
                  {/* Briefcase body */}
                  <rect x="8" y="18" width="32" height="22" rx="2" fill="white"/>
                  {/* Inner clipboard/paper */}
                  <rect x="12" y="22" width="24" height="14" rx="1" fill="#e0e7ff"/>
                  {/* Handle */}
                  <path d="M18,18 L18,14 C18,12.8954 18.8954,12 20,12 L28,12 C29.1046,12 30,12.8954 30,14 L30,18" stroke="white" strokeWidth="2.5" fill="none"/>
                  {/* Task lines */}
                  <line x1="16" y1="26" x2="24" y2="26" stroke="#667eea" strokeWidth="2"/>
                  <rect x="14" y="24" width="3" height="3" rx="0.5" fill="#10b981"/>
                  <line x1="16" y1="30" x2="24" y2="30" stroke="#667eea" strokeWidth="2"/>
                  <rect x="14" y="28" width="3" height="3" rx="0.5" fill="#10b981"/>
                  <line x1="16" y1="34" x2="24" y2="34" stroke="#667eea" strokeWidth="2"/>
                  <rect x="14" y="32" width="3" height="3" rx="0.5" fill="#10b981"/>
                  {/* Clock badge */}
                  <circle cx="38" cy="38" r="7" fill="white" stroke="#667eea" strokeWidth="2"/>
                  <path d="M38,34 L38,38 L41,38" stroke="#667eea" strokeWidth="1.5" fill="none" strokeLinecap="round"/>
                </svg>
              </div>
              
              
              {/* OPTION 2: Simple icon alternative - uncomment to use
              <div className="header-logo">
                📋
              </div>
              */}
              
              {/* OPTION 3: Checkboxes icon - uncomment to use
              <div className="header-logo">
                <svg viewBox="0 0 48 48" className="logo-svg">
                  <rect x="6" y="10" width="12" height="12" rx="2" fill="none" stroke="white" strokeWidth="2"/>
                  <polyline points="9,16 11,18 15,14" fill="none" stroke="white" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                  <rect x="6" y="26" width="12" height="12" rx="2" fill="none" stroke="white" strokeWidth="2"/>
                  <polyline points="9,32 11,34 15,30" fill="none" stroke="white" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                  <line x1="22" y1="16" x2="42" y2="16" stroke="white" strokeWidth="2" strokeLinecap="round"/>
                  <line x1="22" y1="32" x2="42" y2="32" stroke="white" strokeWidth="2" strokeLinecap="round"/>
                </svg>
              </div>
              */}
              
              <div className="header-text">
                <h1>TASK MANAGER PRO</h1>
              </div>
            </div>
            <div className="user-info">
              <span>Welcome, {user.name}</span>
              <button onClick={handleLogout} className="logout-btn">
                Sign Out
              </button>
            </div>
          </div>
        </div>
      </header>

      {/* Hero Section */}
      <div className="app-hero">
        <div className="hero-content">
          <h2 className="hero-title">Your Day, Organized.</h2>
          <p className="hero-subtitle">A smarter way to plan and complete what matters most.</p>
        </div>
      </div>

      <main className="app-main">
        {/* Professional Task Creation Form */}
        <div className="task-form-container">
          <h2 className="form-title">Create New Task</h2>
          <p className="form-subtitle">Add a task to get started</p>
          <form onSubmit={addTask} className="task-form">
            <div className="form-group">
              <label htmlFor="task-name" className="form-label">Task Name *</label>
              <input
                id="task-name"
                type="text"
                value={newTask}
                onChange={(e) => setNewTask(e.target.value)}
                placeholder="Enter task name..."
                className="task-input"
                required
              />
            </div>

            <div className="form-row">
              <div className="form-group">
                <label htmlFor="task-priority" className="form-label">Priority</label>
                <select
                  id="task-priority"
                  value={newTaskPriority}
                  onChange={(e) => setNewTaskPriority(e.target.value)}
                  className="priority-select"
                >
                  <option value="LOW">Low</option>
                  <option value="MEDIUM">Medium</option>
                  <option value="HIGH">High</option>
                </select>
              </div>

              <div className="form-group">
                <label htmlFor="task-due-date" className="form-label">Scheduled Date</label>
                <div className="date-input-group">
                  <input
                    id="task-due-date"
                    type="datetime-local"
                    value={tempDueDate}
                    onChange={(e) => setTempDueDate(e.target.value)}
                    className="due-date-input"
                  />
                  <button
                    type="button"
                    onClick={handleConfirmDate}
                    className="date-confirm-btn"
                    disabled={!tempDueDate}
                    title="Apply selected date"
                  >
                    ✓
                  </button>
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