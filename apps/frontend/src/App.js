import React, { useState, useEffect } from 'react';
import axios from 'axios';
import API_BASE_URL from './config';
import './App.css';

const formatDateForBackend = (dateString) => {
  if (!dateString) return null;
  // Input: "2025-11-10T21:52" (from datetime-local)
  // Output: "2025-11-10T21:52:00" (what backend expects)
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
  const [editingTask, setEditingTask] = useState(null);

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
      console.log('🔍 Attempting to fetch user with token');
      console.log('🌐 API URL:', `${API_BASE_URL}/api/auth/me`);

      const { data, status } = await axios.get(`${API_BASE_URL}/api/auth/me`, {
        headers: { Authorization: `Bearer ${token}` }
      });

      console.log('✅ Auth Success!');
      console.log('📊 Response Status:', status);
      console.log('📦 Response Data:', data);
      console.log('👤 User Info:', data);

      setUser(data);
      fetchTasks(token);
    } catch (error) {
      console.error('\n❌ === Auth Failed ===');
      console.error('💥 Error Message:', error.message);
      console.error('📡 Error Response:', error.response?.data);
      console.error('🔢 Status Code:', error.response?.status);
      console.error('🌐 Request URL:', error.config?.url);
      try {
        console.error('📋 Full Error Object:', JSON.stringify(error, null, 2));
      } catch (_) {}

      console.log('🧹 Cleaning up localStorage...');
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
    console.log('🔑 Using token for tasks request');
    console.log('🌐 Tasks Endpoint:', `${API_BASE_URL}/api/tasks`);

    try {
      const response = await axios.get(`${API_BASE_URL}/api/tasks`, {
        headers: { Authorization: `Bearer ${token}` }
      });

      console.log('✅ Tasks fetched successfully');
      console.log('📊 Number of tasks:', response.data.length);
      console.log('📦 Tasks data:', response.data);

      setTasks(response.data);
    } catch (error) {
      console.error('❌ Error fetching tasks:', error.message);
      console.error('Response:', error.response?.data);
    } finally {
      setLoading(false);
    }
  };

  const addTask = async (e) => {
    e.preventDefault();
    console.log('\n➕ === Adding New Task ===');
    console.log('📝 Task title:', newTask);
    console.log('📝 Priority:', newTaskPriority);
    console.log('📝 Due Date:', newTaskDueDate);
  
    if (!newTask.trim()) {
      console.log('⚠️ Task title is empty - aborting');
      return;
    }
  
    const token = authToken || localStorage.getItem('jwt_token');
    console.log('🌐 POST to:', `${API_BASE_URL}/api/tasks`);
  
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
      console.log('📦 New task:', response.data);
  
      setTasks([...tasks, response.data]);
      setNewTask('');
      setNewTaskPriority('MEDIUM');
      setNewTaskDueDate('');
    } catch (error) {
      console.error('❌ Error adding task:', error.message);
      console.error('Response:', error.response?.data);
    }
  };

  const updateTask = async (id, updates) => {
    console.log('\n✏️ === Updating Task ===');
    console.log('🆔 Task ID:', id);
    console.log('📝 Updates:', updates);

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
      console.log('📦 Updated task:', response.data);
      
      setTasks(tasks.map(t => (t.id === id ? response.data : t)));
      setEditingTask(null);
    } catch (error) {
      console.error('❌ Error updating task:', error.message);
      console.error('Response:', error.response?.data);
    }
  };

  const toggleTask = async (id) => {
    console.log('\n🔄 === Toggling Task ===');
    console.log('🆔 Task ID:', id);

    const token = authToken || localStorage.getItem('jwt_token');

    try {
      const task = tasks.find(t => t.id === id);
      console.log('📝 Current task status:', task.completed ? 'Completed' : 'Pending');
      console.log('🌐 PUT to:', `${API_BASE_URL}/api/tasks/${id}`);

      const response = await axios.put(
        `${API_BASE_URL}/api/tasks/${id}`,
        { ...task, completed: !task.completed },
        { headers: { Authorization: `Bearer ${token}` } }
      );

      console.log('✅ Task toggled successfully');
      console.log('📦 Updated task:', response.data);

      setTasks(tasks.map(t => (t.id === id ? response.data : t)));
    } catch (error) {
      console.error('❌ Error updating task:', error.message);
      console.error('Response:', error.response?.data);
    }
  };

  const deleteTask = async (id) => {
    console.log('\n🗑️ === Deleting Task ===');
    console.log('🆔 Task ID:', id);

    const token = authToken || localStorage.getItem('jwt_token');
    console.log('🌐 DELETE to:', `${API_BASE_URL}/api/tasks/${id}`);

    try {
      await axios.delete(`${API_BASE_URL}/api/tasks/${id}`, {
        headers: { Authorization: `Bearer ${token}` }
      });

      console.log('✅ Task deleted successfully');

      setTasks(tasks.filter(t => t.id !== id));
    } catch (error) {
      console.error('❌ Error deleting task:', error.message);
      console.error('Response:', error.response?.data);
    }
  };

  const handleLogin = () => {
    console.log('\n🔐 === Login Button Clicked ===');
    console.log('🌐 Redirecting to:', `${API_BASE_URL}/oauth2/authorization/google`);
    console.log('🔧 Full API_BASE_URL:', API_BASE_URL);
    window.location.href = `${API_BASE_URL}/oauth2/authorization/google`;
  };

  const handleFacebookLogin = () => {
    console.log('\n🔐 === Facebook Login Button Clicked ===');
    console.log('🌐 Redirecting to:', `${API_BASE_URL}/oauth2/authorization/facebook`);
    console.log('🔧 Full API_BASE_URL:', API_BASE_URL);
    window.location.href = `${API_BASE_URL}/oauth2/authorization/facebook`;
  };

  const handleLogout = () => {
    console.log('\n👋 === Logout Initiated ===');
    console.log('🧹 Clearing localStorage...');

    try {
      localStorage.removeItem('jwt_token');
      localStorage.removeItem('user');
    } catch (_) {}
    setUser(null);
    setTasks([]);
    setAuthToken(null);

    console.log('✅ Logged out successfully');
  };

  // Helper function to format dates nicely
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
    console.log('⏳ App is in loading state...');
    return <div className="loading">Loading...</div>;
  }

  if (!user) {
    const params = new URLSearchParams(window.location.search);
    const loginError = params.get('loginError');

    console.log('\n🔓 === Showing Login Screen ===');
    console.log('📍 Current URL:', window.location.href);
    console.log('🔗 URL Search Params:', window.location.search);
    console.log('❌ Login Error Parameter:', loginError);
    console.log('🎫 Token in localStorage:', localStorage.getItem('jwt_token') || 'NONE');
    console.log('👤 User in localStorage:', localStorage.getItem('user') || 'NONE');

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
            Sign in with Facebook
          </button>
        </div>
      </div>
    );
  }

  console.log('\n✅ === Rendering Main App ===');
  console.log('👤 Logged in user:', user.name || user.email);
  console.log('📋 Total tasks:', tasks.length);

  return (
    <div className="app">
      <header className="app-header">
        <h1>Task Manager</h1>
        <div className="user-info">
          <span>Welcome, {user.name}</span>
          <button onClick={handleLogout} className="logout-btn">
            Logout
          </button>
        </div>
      </header>

      <main className="app-main">
        {/* Professional Task Creation Form with Labels */}
        <div className="task-form-container">
          <h2 className="form-title">Create New Task</h2>
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
                <input
                  id="task-due-date"
                  type="datetime-local"
                  value={newTaskDueDate}
                  onChange={(e) => setNewTaskDueDate(e.target.value)}
                  className="due-date-input"
                />
              </div>
            </div>

            <button type="submit" className="add-btn">
              <span className="btn-icon">+</span> Add Task
            </button>
          </form>
        </div>

        {/* Task List with Professional Layout */}
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
              {/* Table Header */}
              <div className="task-table-header">
                <div className="th-status">Status</div>
                <div className="th-name">Task Name</div>
                <div className="th-priority">Priority</div>
                <div className="th-scheduled">Scheduled Date</div>
                <div className="th-actions">Actions</div>
              </div>

              {/* Table Body */}
              <div className="task-table-body">
                {tasks.map(task => (
                  <div key={task.id} className={`task-row ${task.completed ? 'completed-task' : ''}`}>
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

                    <div className="td-actions">
                      <button
                        onClick={() => setEditingTask(task)}
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

      {/* Professional Edit Modal */}
      {editingTask && (
        <div className="modal-overlay" onClick={() => setEditingTask(null)}>
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
                <input
                  id="edit-task-due-date"
                  type="datetime-local"
                  value={editingTask.dueDate ? new Date(editingTask.dueDate).toISOString().slice(0, 16) : ''}
                  onChange={(e) => setEditingTask({...editingTask, dueDate: e.target.value})}
                  className="modal-input"
                />
              </div>

              <div className="modal-actions">
                <button type="submit" className="modal-btn-save">
                  💾 Save Changes
                </button>
                <button type="button" onClick={() => setEditingTask(null)} className="modal-btn-cancel">
                  ✖ Cancel
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}

export default App;