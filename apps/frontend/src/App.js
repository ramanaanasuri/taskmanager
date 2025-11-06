import React, { useState, useEffect } from 'react';
import axios from 'axios';
import API_BASE_URL from './config';
import './App.css';

function App() {
  const [tasks, setTasks] = useState([]);
  const [newTask, setNewTask] = useState('');
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);
  const [authToken, setAuthToken] = useState(null); // <- single source of truth for token

  // ============ DEBUG: Component Mount ============
  useEffect(() => {
    console.log('=== 🚀 App Component Mounted ===');
    console.log('🔧 API_BASE_URL:', API_BASE_URL);
    console.log('📍 Current URL:', window.location.href);
    console.log('🔗 URL Search Params:', window.location.search);
  }, []);

  /**
   * Fallback: if the backend ever redirects to "/" with ?token=...
   * (normally your success handler goes to /oauth2/redirect, but this is a safe belt-and-suspenders)
   * - store token
   * - update state
   * - clean the URL
   * Otherwise, initialize from localStorage.
   */
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

      // Clean the URL (remove token)
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

  // Whenever we have/lose a token, (re)run auth check
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

    if (!newTask.trim()) {
      console.log('⚠️ Task title is empty - aborting');
      return;
    }

    const token = authToken || localStorage.getItem('jwt_token');
    console.log('🌐 POST to:', `${API_BASE_URL}/api/tasks`);

    try {
      const response = await axios.post(
        `${API_BASE_URL}/api/tasks`,
        { title: newTask, completed: false },
        { headers: { Authorization: `Bearer ${token}` } }
      );

      console.log('✅ Task added successfully');
      console.log('📦 New task:', response.data);

      setTasks([...tasks, response.data]);
      setNewTask('');
    } catch (error) {
      console.error('❌ Error adding task:', error.message);
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

    // IMPORTANT: navigation (not XHR) so the browser follows 302 → Google
    window.location.href = `${API_BASE_URL}/oauth2/authorization/google`;
  };

  const handleFacebookLogin = () => {
    console.log('\n🔐 === Facebook Login Button Clicked ===');
    console.log('🌐 Redirecting to:', `${API_BASE_URL}/oauth2/authorization/facebook`);
    console.log('🔧 Full API_BASE_URL:', API_BASE_URL);

    // IMPORTANT: navigation (not XHR) so the browser follows 302 → Facebook
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

  if (loading) {
    console.log('⏳ App is in loading state...');
    return <div className="loading">Loading...</div>;
  }

  if (!user) {
    // Show a friendly message if backend sent us back with ?loginError=1
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
        <form onSubmit={addTask} className="task-form">
          <input
            type="text"
            value={newTask}
            onChange={(e) => setNewTask(e.target.value)}
            placeholder="Add a new task..."
            className="task-input"
          />
          <button type="submit" className="add-btn">Add Task</button>
        </form>

        <div className="tasks-container">
          {tasks.length === 0 ? (
            <p className="no-tasks">No tasks yet. Add one above!</p>
          ) : (
            <ul className="task-list">
              {tasks.map(task => (
                <li key={task.id} className="task-item">
                  <input
                    type="checkbox"
                    checked={task.completed}
                    onChange={() => toggleTask(task.id)}
                    className="task-checkbox"
                  />
                  <span className={task.completed ? 'completed' : ''}>
                    {task.title}
                  </span>
                  <button
                    onClick={() => deleteTask(task.id)}
                    className="delete-btn"
                  >
                    Delete
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
      </main>
    </div>
  );
}

export default App;
