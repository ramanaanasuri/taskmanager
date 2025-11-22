import React, { useState, useEffect } from 'react';
import axios from 'axios';
import API_BASE_URL from './config';
import './App.css';
import { subscribeToPushNotifications } from './utils/pushNotifications';
import { convertLocalToUTC } from './utils/dateUtils';  //ADDED - for timezone conversion

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
  const [enableNotifications, setEnableNotifications] = useState(false);
  const [newTaskPhone, setNewTaskPhone] = useState('');  

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

  // ============ ADDED: Service Worker Message Listener ============
  useEffect(() => {
    console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━');
    console.log('[APP DEBUG] 1. Setting up message listener');
    
    if ('serviceWorker' in navigator) {
      console.log('[APP DEBUG] 2. Service Worker API available');
      
      const messageHandler = (event) => {
        console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━');
        console.log('[APP DEBUG] 3. Message received at:', new Date().toISOString());
        console.log('[APP DEBUG] 4. Event:', event);
        console.log('[APP DEBUG] 5. Event.data:', event.data);
        console.log('[APP DEBUG] 6. Event.origin:', event.origin);
        console.log('[APP DEBUG] 7. Event.source:', event.source);
        
        if (event.data) {
          console.log('[APP DEBUG] 8. Message type:', event.data.type);
          console.log('[APP DEBUG] 9. Message taskId:', event.data.taskId);
          
          if (event.data.type === 'NOTIFICATION_CLICK') {
            console.log('[APP DEBUG] 10. ✅ NOTIFICATION_CLICK detected');
            const taskId = event.data.taskId;
            console.log('[APP DEBUG] 11. Task ID:', taskId);
            
            console.log('[APP DEBUG] 12. Current location:', window.location.href);
            console.log('[APP DEBUG] 13. Document readyState:', document.readyState);
            console.log('[APP DEBUG] 14. Loading state before reload:', loading);
            
            console.log('[APP DEBUG] 15. Calling window.location.reload()...');
            window.location.reload();
            console.log('[APP DEBUG] 16. Reload called (may not execute due to reload)');
          } else {
            console.log('[APP DEBUG] 10. ⚠️ Unknown message type:', event.data.type);
          }
        } else {
          console.log('[APP DEBUG] 8. ⚠️ No data in message');
        }
        console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━');
      };
      
      navigator.serviceWorker.addEventListener('message', messageHandler);
      console.log('[APP DEBUG] 17. ✅ Message listener registered');
      
      // Check if SW is already registered
      navigator.serviceWorker.getRegistration().then(reg => {
        if (reg) {
          console.log('[APP DEBUG] 18. SW already registered');
          console.log('[APP DEBUG] 18a. SW scope:', reg.scope);
          console.log('[APP DEBUG] 18b. SW active:', reg.active ? 'YES' : 'NO');
        } else {
          console.log('[APP DEBUG] 18. ⚠️ No SW registered yet');
        }
      });
      
      return () => {
        navigator.serviceWorker.removeEventListener('message', messageHandler);
        console.log('[APP DEBUG] 19. Message listener removed');
      };
    } else {
      console.log('[APP DEBUG] 2. ❌ Service Worker API NOT available');
    }
    console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━');
  }, []);
  
  useEffect(() => {
    console.log('[APP DEBUG] Loading state changed:', loading);
    
    // If stuck in loading state for more than 5 seconds, alert
    if (loading) {
      const timer = setTimeout(() => {
        console.error('[APP DEBUG] ⚠️ STUCK IN LOADING STATE for 5+ seconds!');
        console.log('[APP DEBUG] Current state:', {
          loading,
          user,
          authToken,
          tasks: tasks.length
        });
      }, 5000);
      
      return () => clearTimeout(timer);
    }
  }, [loading]);

// ============ ADDED: Handle taskId from Notification Click ============
// When user clicks notification, sw.js navigates to /?taskId=123
// This effect detects that taskId and highlights the corresponding task
useEffect(() => {
  const params = new URLSearchParams(window.location.search);
  const taskIdFromNotification = params.get('taskId');
  
  if (taskIdFromNotification) {
    console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━');
    console.log('[APP DEBUG - NOTIFICATION] 📌 Task ID from URL:', taskIdFromNotification);
    console.log('[APP DEBUG - NOTIFICATION] Current tasks loaded:', tasks.length);
    
    // Wait for tasks to load, then scroll to and highlight the task
    if (tasks.length > 0) {
      setTimeout(() => {
        const taskElement = document.querySelector(`[data-task-id="${taskIdFromNotification}"]`);
        
        if (taskElement) {
          console.log('[APP DEBUG - NOTIFICATION] ✅ Task element found, highlighting...');
          
          // Scroll to task
          taskElement.scrollIntoView({ behavior: 'smooth', block: 'center' });
          
          // Highlight with yellow background
          taskElement.style.backgroundColor = '#fff3cd';
          taskElement.style.transition = 'background-color 0.3s ease';
          
          console.log('[APP DEBUG - NOTIFICATION] ✅ Task highlighted:', taskIdFromNotification);
          
          // Remove highlight after 3 seconds
          setTimeout(() => {
            taskElement.style.backgroundColor = '';
            console.log('[APP DEBUG - NOTIFICATION] 🔄 Highlight removed');
          }, 3000);
        } else {
          console.warn('[APP DEBUG - NOTIFICATION] ⚠️ Task element not found in DOM');
          console.log('[APP DEBUG - NOTIFICATION] Available task IDs:', 
            Array.from(document.querySelectorAll('[data-task-id]')).map(el => el.getAttribute('data-task-id'))
          );
        }
      }, 500); // Wait 500ms for DOM to be ready
      
      // Clean URL - remove taskId parameter to keep URL clean
      window.history.replaceState({}, document.title, '/');
      console.log('[APP DEBUG - NOTIFICATION] 🔄 URL cleaned (taskId parameter removed)');
    }
    console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━');
  }
}, [tasks]); // Re-run when tasks array changes (after fetch completes)
// ============ End TaskId Handler ============  
  // Register Service Worker on App Mount
  useEffect(() => {
    if ('serviceWorker' in navigator) {
      console.log('[APP DEBUG] Registering service worker...');
      
      navigator.serviceWorker.register('/sw.js')
        .then(registration => {
          console.log('[APP DEBUG] ✅ SW registered successfully');
          console.log('[APP DEBUG] Scope:', registration.scope);
          registration.update(); // Check for updates
        })
        .catch(error => {
          console.error('[APP DEBUG] ❌ SW registration failed:', error);
        });
    } else {
      console.log('[APP DEBUG] ⚠️ Service workers not supported');
    }
  }, []); // Empty array = run once on mount    
  // ============ End Added Code ============

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

// REPLACE YOUR addTask FUNCTION WITH THIS DEBUG VERSION

const addTask = async (e) => {
  e.preventDefault();
  console.log('\n🎯 ═══════════════════════════════════════');
  console.log('🎯 addTask FUNCTION CALLED');
  console.log('🎯 ═══════════════════════════════════════');

  if (!newTask.trim()) {
    console.log('⚠️ Task title is empty - aborting');
    return;
  }

  const token = authToken || localStorage.getItem('jwt_token');
  
  console.log('📝 newTaskDueDate STATE:', newTaskDueDate);
  console.log('📝 Type:', typeof newTaskDueDate);
  
  // Call the conversion function
  console.log('🔄 Calling convertLocalToUTC...');
  const formattedDueDate = convertLocalToUTC(newTaskDueDate);
  
  console.log('✅ Conversion complete!');
  console.log('📤 formattedDueDate:', formattedDueDate);
  console.log('📤 Type:', typeof formattedDueDate);

  // ADD: Subscribe to push notifications if enabled
  if (enableNotifications) {
    console.log('📞 Notifications enabled - subscribing to push...');
    try {
      await subscribeToPushNotifications(API_BASE_URL, token);
      console.log('✅ Push subscription created successfully');
    } catch (error) {
      console.error('❌ Failed to subscribe to push notifications:', error);
      alert('Failed to enable notifications. Please check browser permissions and try again.');
      return; // Don't create task if subscription fails
    }
  }  

  // Build the request payload
  const payload = {
    title: newTask,
    priority: newTaskPriority,
    dueDate: formattedDueDate,
    completed: false,
    notificationsEnabled: enableNotifications,
    phoneNumber: newTaskPhone || null,
    smsEnabled: false
  };

  console.log('📦 ═══════════════════════════════════════');
  console.log('📦 REQUEST PAYLOAD TO BACKEND:');
  console.log(JSON.stringify(payload, null, 2));
  console.log('📦 ═══════════════════════════════════════');

  try {
    console.log('🚀 Sending POST request to:', `${API_BASE_URL}/api/tasks`);
    
    const response = await axios.post(
      `${API_BASE_URL}/api/tasks`,
      payload,
      {
        headers: { Authorization: `Bearer ${token}` }
      }
    );

    console.log('✅ ═══════════════════════════════════════');
    console.log('✅ SUCCESS! Task added!');
    console.log('✅ Response:', response.data);
    console.log('✅ ═══════════════════════════════════════');
    
    setTasks([...tasks, response.data]);
    setNewTask('');
    setNewTaskPriority('MEDIUM');
    setNewTaskDueDate('');
    setTempDueDate('');
    setEnableNotifications(false);
    setNewTaskPhone('');
  } catch (error) {
    console.error('❌ ═══════════════════════════════════════');
    console.error('❌ ERROR adding task!');
    console.error('❌ Error message:', error.message);
    console.error('❌ Full error:', error);
    if (error.response) {
      console.error('❌ Response status:', error.response.status);
      console.error('❌ Response data:', error.response.data);
    }
    console.error('❌ ═══════════════════════════════════════');
  }
};

const updateTask = async (id, updates) => {
  console.log('\n' + '='.repeat(60));
  console.log('✏️ === UPDATING TASK ===');
  console.log('='.repeat(60));
  console.log('🆔 Task ID:', id);
  console.log('📝 Updates received:', JSON.stringify(updates, null, 2));

  const token = authToken || localStorage.getItem('jwt_token');

  try {
    const task = tasks.find(t => t.id === id);
    
    if (!task) {
      console.error('❌ Task not found in state!');
      return;
    }

    console.log('\n📋 === ORIGINAL TASK FROM STATE ===');
    console.log('Full task object:', JSON.stringify(task, null, 2));
    console.log('Task completed value:', task.completed);
    console.log('Task completed type:', typeof task.completed);
    console.log('Task completed === true:', task.completed === true);
    console.log('Task completed === false:', task.completed === false);
    console.log('Task completed === null:', task.completed === null);
    console.log('Task completed === undefined:', task.completed === undefined);
    
    // Convert date to UTC if provided
    let formattedDueDate = updates.dueDate;
    if (formattedDueDate && typeof formattedDueDate === 'string') {
      console.log('\n📅 === DATE CONVERSION ===');
      console.log('Input dueDate:', formattedDueDate);
      formattedDueDate = convertLocalToUTC(formattedDueDate);
      console.log('Converted to UTC:', formattedDueDate);
    }

    // Build payload EXPLICITLY
    const payload = {
      title: updates.title !== undefined ? updates.title : task.title,
      priority: updates.priority !== undefined ? updates.priority : task.priority,
      dueDate: formattedDueDate !== undefined ? formattedDueDate : task.dueDate,
      completed: task.completed === true,  // Explicit boolean conversion
      notificationsEnabled: task.notificationsEnabled === true,
      phoneNumber: task.phoneNumber || null,
      smsEnabled: task.smsEnabled === true
    };

    console.log('\n📤 === PAYLOAD TO BACKEND ===');
    console.log('Full payload:', JSON.stringify(payload, null, 2));
    console.log('Payload completed value:', payload.completed);
    console.log('Payload completed type:', typeof payload.completed);

    console.log('\n🚀 Sending PUT request to:', `${API_BASE_URL}/api/tasks/${id}`);

    const response = await axios.put(
      `${API_BASE_URL}/api/tasks/${id}`,
      payload,
      { headers: { Authorization: `Bearer ${token}` } }
    );

    console.log('\n✅ === SUCCESS ===');
    console.log('Response status:', response.status);
    console.log('Response data:', JSON.stringify(response.data, null, 2));
    console.log('Response completed value:', response.data.completed);
    
    setTasks(tasks.map(t => (t.id === id ? response.data : t)));
    setEditingTask(null);
    setTempEditDate('');
    
    console.log('✅ State updated successfully');
    console.log('='.repeat(60));
  } catch (error) {
    console.error('\n❌ === ERROR ===');
    console.error('Error message:', error.message);
    console.error('Error stack:', error.stack);
    if (error.response) {
      console.error('Response status:', error.response.status);
      console.error('Response data:', error.response.data);
      console.error('Response headers:', error.response.headers);
    }
    console.error('='.repeat(60));
  }
};

const toggleTask = async (id) => {
  console.log('\n' + '='.repeat(60));
  console.log('🔄 === TOGGLING TASK (CHECKBOX) ===');
  console.log('='.repeat(60));
  console.log('🆔 Task ID:', id);

  const token = authToken || localStorage.getItem('jwt_token');

  try {
    const task = tasks.find(t => t.id === id);
    
    if (!task) {
      console.error('❌ Task not found in state!');
      return;
    }

    console.log('\n📋 === ORIGINAL TASK FROM STATE ===');
    console.log('Full task object:', JSON.stringify(task, null, 2));
    console.log('Task title:', task.title);
    console.log('Task completed value (BEFORE toggle):', task.completed);
    console.log('Task completed type:', typeof task.completed);
    console.log('Task completed === true:', task.completed === true);
    console.log('Task completed === false:', task.completed === false);
    console.log('Task completed === null:', task.completed === null);
    console.log('Task completed === undefined:', task.completed === undefined);

    // Build payload EXPLICITLY
    const payload = {
      title: task.title,
      priority: task.priority,
      dueDate: task.dueDate,
      completed: !task.completed,  // FLIP the completed status
      notificationsEnabled: task.notificationsEnabled === true,
      phoneNumber: task.phoneNumber || null,
      smsEnabled: task.smsEnabled === true
    };

    console.log('\n🔄 === TOGGLE LOGIC ===');
    console.log('Original completed:', task.completed);
    console.log('Flipped to (!task.completed):', !task.completed);
    console.log('Final payload completed:', payload.completed);
    console.log('Expected result:', task.completed ? 'TRUE → FALSE (uncheck)' : 'FALSE → TRUE (check)');

    console.log('\n📤 === PAYLOAD TO BACKEND ===');
    console.log('Full payload:', JSON.stringify(payload, null, 2));
    console.log('Payload completed value:', payload.completed);
    console.log('Payload completed type:', typeof payload.completed);

    console.log('\n🚀 Sending PUT request to:', `${API_BASE_URL}/api/tasks/${id}`);

    const response = await axios.put(
      `${API_BASE_URL}/api/tasks/${id}`,
      payload,
      { headers: { Authorization: `Bearer ${token}` } }
    );

    console.log('\n✅ === SUCCESS ===');
    console.log('Response status:', response.status);
    console.log('Response data:', JSON.stringify(response.data, null, 2));
    console.log('Response completed value (AFTER toggle):', response.data.completed);
    console.log('Toggle worked correctly:', 
                task.completed === true && response.data.completed === false ? '✅ YES (checked → unchecked)' :
                task.completed === false && response.data.completed === true ? '✅ YES (unchecked → checked)' :
                '❌ NO - Something went wrong!');
    
    setTasks(tasks.map(t => (t.id === id ? response.data : t)));
    
    console.log('✅ State updated successfully');
    console.log('='.repeat(60));
  } catch (error) {
    console.error('\n❌ === ERROR ===');
    console.error('Error message:', error.message);
    console.error('Error stack:', error.stack);
    if (error.response) {
      console.error('Response status:', error.response.status);
      console.error('Response data:', error.response.data);
      console.error('Response headers:', error.response.headers);
    }
    console.error('='.repeat(60));
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
            {/* Notification Option */}
            <div className="form-group">
              <label className="notification-label" style={{
                display: 'flex',
                alignItems: 'center',
                gap: '0.5rem',
                cursor: 'pointer',
                fontSize: '0.95rem'
              }}>
                <input
                  type="checkbox"
                  checked={enableNotifications}
                  onChange={(e) => setEnableNotifications(e.target.checked)}
                  style={{
                    width: '18px',
                    height: '18px',
                    cursor: 'pointer'
                  }}
                />
                <span>🔔 Enable push notifications for this task</span>
              </label>
              <small style={{
                display: 'block',
                color: '#666',
                fontSize: '0.85rem',
                marginTop: '0.25rem',
                marginLeft: '1.5rem'
              }}>
                You'll be asked for browser notification permission when you add the task
              </small>
            </div>

            {/* Phone Number Input - Only shown when notifications enabled */}
            {enableNotifications && (
            <div className="form-group">
              <label htmlFor="task-phone" className="form-label">
                Phone Number <span style={{color: '#999', fontWeight: 'normal'}}>(Optional - for future SMS)</span>
              </label>
              <div className="phone-input-wrapper">
                <input
                  id="task-phone"
                  type="tel"
                  className="form-input"
                  placeholder="+1 (555) 123-4567"
                  value={newTaskPhone}
                  onChange={(e) => setNewTaskPhone(e.target.value)}
                  pattern="[+]?[0-9\s\-\(\)]+"
                />
              </div>
              <small style={{
                display: 'block',
                color: '#64748b',
                fontSize: '0.85rem',
                marginTop: '0.35rem'
              }}>
                💡 We'll use this for SMS notifications in the future (Web Push works without it)
              </small>
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
                    <div 
                      key={task.id} 
                      className={`task-row ${task.completed ? 'completed-task' : ''}`}
                      data-task-id={task.id}
                    >
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
                            <span className="notification-badge" title="Notifications enabled">
                              🔔
                            </span>
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