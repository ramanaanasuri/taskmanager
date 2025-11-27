/**
 * SubscriptionModal.js
 * Stripe subscription management component for Task Manager Pro
 * 
 * Usage: Import and add to App.js with minimal changes
 */
import React, { useState, useEffect } from 'react';
import axios from 'axios';
import API_BASE_URL from './config';

// ============ Subscription Modal Component ============
function SubscriptionModal({ isOpen, onClose, authToken, user }) {
  const [plans, setPlans] = useState(null);
  const [subscription, setSubscription] = useState(null);
  const [loading, setLoading] = useState(true);
  const [processing, setProcessing] = useState(false);
  const [error, setError] = useState(null);

  // Fetch plans and current subscription
  useEffect(() => {
    if (isOpen && authToken) {
      fetchData();
    }
  }, [isOpen, authToken]);

  const fetchData = async () => {
    setLoading(true);
    setError(null);
    try {
      // Fetch available plans (public endpoint)
      const plansRes = await axios.get(`${API_BASE_URL}/api/stripe/plans`);
      setPlans(plansRes.data);
      console.log('📋 [Subscription] Plans loaded:', plansRes.data);

      // Fetch current subscription
      const subRes = await axios.get(`${API_BASE_URL}/api/stripe/subscription`, {
        headers: { Authorization: `Bearer ${authToken}` }
      });
      setSubscription(subRes.data);
      console.log('📊 [Subscription] Current subscription:', subRes.data);
    } catch (err) {
      console.error('❌ [Subscription] Error fetching data:', err);
      setError('Failed to load subscription data');
    } finally {
      setLoading(false);
    }
  };

  // Handle plan selection (create checkout session)
  const handleSelectPlan = async (planKey) => {
    if (planKey === 'free') return; // Free plan doesn't need checkout
    
    setProcessing(true);
    setError(null);
    
    try {
      console.log('🛒 [Subscription] Creating checkout for plan:', planKey);
      
      const response = await axios.post(
        `${API_BASE_URL}/api/stripe/create-checkout-session`,
        { plan: planKey },
        { headers: { Authorization: `Bearer ${authToken}` }}
      );
      
      console.log('✅ [Subscription] Checkout session created:', response.data);
      
      // Redirect to Stripe Checkout
      window.location.href = response.data.url;
    } catch (err) {
      console.error('❌ [Subscription] Checkout error:', err);
      setError(err.response?.data?.error || 'Failed to start checkout');
      setProcessing(false);
    }
  };

  // Handle manage subscription (open customer portal)
  const handleManageSubscription = async () => {
    setProcessing(true);
    setError(null);
    
    try {
      console.log('🚪 [Subscription] Opening customer portal...');
      
      const response = await axios.post(
        `${API_BASE_URL}/api/stripe/create-portal-session`,
        {},
        { headers: { Authorization: `Bearer ${authToken}` }}
      );
      
      console.log('✅ [Subscription] Portal session created:', response.data);
      
      // Redirect to Stripe Customer Portal
      window.location.href = response.data.url;
    } catch (err) {
      console.error('❌ [Subscription] Portal error:', err);
      setError(err.response?.data?.error || 'Failed to open subscription management');
      setProcessing(false);
    }
  };

  if (!isOpen) return null;

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="subscription-modal" onClick={(e) => e.stopPropagation()}>
        <div className="subscription-header">
          <h2>💎 Premium Plans</h2>
          <button className="modal-close-btn" onClick={onClose}>×</button>
        </div>

        {loading ? (
          <div className="subscription-loading">
            <div className="loading-spinner"></div>
            <p>Loading plans...</p>
          </div>
        ) : error ? (
          <div className="subscription-error">
            <p>⚠️ {error}</p>
            <button onClick={fetchData} className="retry-btn">Try Again</button>
          </div>
        ) : (
          <>
            {/* Current Plan Status */}
            {subscription && (
              <div className="current-plan-banner">
                <span className="plan-badge">{subscription.subscriptionPlan?.toUpperCase() || 'FREE'}</span>
                <span className="plan-status">
                  {subscription.isPremium ? '✅ Active' : '📋 Free Plan'}
                </span>
                {subscription.isPremium && (
                  <button 
                    onClick={handleManageSubscription} 
                    className="manage-btn"
                    disabled={processing}
                  >
                    {processing ? 'Loading...' : '⚙️ Manage'}
                  </button>
                )}
              </div>
            )}

            {/* Usage Stats (for premium users) */}
            {subscription?.isPremium && (
              <div className="usage-stats">
                <div className="usage-item">
                  <span className="usage-label">📱 SMS</span>
                  <span className="usage-value">
                    {subscription.smsCreditsUsed} / {subscription.smsCreditsLimit === -1 ? '∞' : subscription.smsCreditsLimit}
                  </span>
                </div>
                <div className="usage-item">
                  <span className="usage-label">🤖 AI</span>
                  <span className="usage-value">
                    {subscription.aiRequestsUsed} / {subscription.aiRequestsLimit === -1 ? '∞' : subscription.aiRequestsLimit}
                  </span>
                </div>
              </div>
            )}

            {/* Plans Grid */}
            <div className="plans-grid">
              {plans && Object.entries(plans).map(([key, plan]) => (
                <div 
                  key={key} 
                  className={`plan-card ${plan.popular ? 'popular' : ''} ${subscription?.subscriptionPlan === key ? 'current' : ''}`}
                >
                  {plan.popular && <div className="popular-badge">Most Popular</div>}
                  {subscription?.subscriptionPlan === key && <div className="current-badge">Current Plan</div>}
                  
                  <h3 className="plan-name">{plan.name}</h3>
                  
                  <div className="plan-price">
                    <span className="price-amount">${plan.price}</span>
                    {plan.price > 0 && <span className="price-interval">/{plan.interval}</span>}
                  </div>
                  
                  <ul className="plan-features">
                    {plan.features?.map((feature, idx) => (
                      <li key={idx}>✓ {feature}</li>
                    ))}
                  </ul>
                  
                  <button
                    className={`plan-select-btn ${key === 'free' ? 'free-btn' : ''}`}
                    onClick={() => handleSelectPlan(key)}
                    disabled={processing || subscription?.subscriptionPlan === key || key === 'free'}
                  >
                    {processing ? 'Processing...' : 
                     subscription?.subscriptionPlan === key ? 'Current Plan' :
                     key === 'free' ? 'Free Forever' : 
                     'Select Plan'}
                  </button>
                </div>
              ))}
            </div>

            {/* Footer note */}
            <p className="subscription-footer">
              🔒 Secure payments by Stripe • Cancel anytime
            </p>
          </>
        )}
      </div>
    </div>
  );
}

// ============ Upgrade Button Component ============
function UpgradeButton({ onClick, subscription }) {
  const isPremium = subscription?.isPremium;
  
  return (
    <button 
      onClick={onClick} 
      className={`upgrade-btn ${isPremium ? 'premium' : ''}`}
      title={isPremium ? 'Manage subscription' : 'Upgrade to Premium'}
    >
      {isPremium ? '💎' : '⭐'} {isPremium ? 'Premium' : 'Upgrade'}
    </button>
  );
}

// ============ Hook for subscription state ============
function useSubscription(authToken) {
  const [subscription, setSubscription] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (authToken) {
      fetchSubscription();
    }
  }, [authToken]);

  const fetchSubscription = async () => {
    try {
      const response = await axios.get(`${API_BASE_URL}/api/stripe/subscription`, {
        headers: { Authorization: `Bearer ${authToken}` }
      });
      setSubscription(response.data);
      console.log('📊 [useSubscription] Loaded:', response.data);
    } catch (err) {
      console.error('❌ [useSubscription] Error:', err);
    } finally {
      setLoading(false);
    }
  };

  const refresh = () => fetchSubscription();

  return { subscription, loading, refresh };
}

export { SubscriptionModal, UpgradeButton, useSubscription };
export default SubscriptionModal;