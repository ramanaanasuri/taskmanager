import { useEffect } from 'react';

function OAuth2Redirect() {
  useEffect(() => {
    // Get token from URL query parameter
    const urlParams = new URLSearchParams(window.location.search);
    const token = urlParams.get('token');
    const error = urlParams.get('error');

    if (error) {
      console.error('OAuth error:', error);
      window.location.href = '/';
      return;
    }

    if (token) {
      // Store JWT token
      localStorage.setItem('jwt_token', token);
      
      // Redirect to home page (will trigger checkAuth)
      window.location.href = '/';
    } else {
      console.error('No token received');
      window.location.href = '/';
    }
  }, []);

  return (
    <div style={{ textAlign: 'center', marginTop: '100px' }}>
      <h2>Logging you in...</h2>
      <p>Please wait...</p>
    </div>
  );
}

export default OAuth2Redirect;