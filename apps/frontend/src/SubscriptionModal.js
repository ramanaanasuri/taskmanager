/**
 * SubscriptionModal.js
 * Stripe subscription management component for Task Manager Pro
 * 
 * Features:
 * - View available plans (Free, Basic, Pro, Enterprise)
 * - Subscribe to paid plans via Stripe Checkout
 * - Cancel subscription / Downgrade to Free (IN-APP - no redirect!)
 * - Manage subscription via Stripe Portal (for payment method updates)
 * - View usage stats (SMS, AI credits)
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
  const [processingPlan, setProcessingPlan] = useState(null); // which plan's checkout is in flight
  const [billingLoading, setBillingLoading] = useState(false); // customer-portal redirect in flight
  const [error, setError] = useState(null);
  
  //Added: State for cancel confirmation dialog
  const [showCancelConfirm, setShowCancelConfirm] = useState(false);
  const [cancelProcessing, setCancelProcessing] = useState(false);

  // Fetch plans and current subscription + Reset states when modal opens
  useEffect(() => {
    if (isOpen && authToken) {
      // Reset processing states when modal opens (fixes stuck state after browser back)
      setProcessingPlan(null);
      setBillingLoading(false);
      setCancelProcessing(false);
      setError(null);
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
    //Modified: Handle free plan click - show cancel confirmation instead of returning
    if (planKey === 'free') {
      // If user is on a paid plan and can cancel, show confirmation dialog
      if (subscription?.canCancel) {
        setShowCancelConfirm(true);
      }
      return;
    }
    
    setProcessingPlan(planKey);
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
      setProcessingPlan(null);
    }
  };

  //Added: Handle cancel subscription (IN-APP - no redirect to Stripe!)
  const handleCancelSubscription = async () => {
    setCancelProcessing(true);
    setError(null);
    
    try {
      console.log('📛 [Subscription] Canceling subscription...');
      
      const response = await axios.post(
        `${API_BASE_URL}/api/stripe/cancel-subscription`,
        {},
        { headers: { Authorization: `Bearer ${authToken}` }}
      );
      
      console.log('✅ [Subscription] Canceled:', response.data);
      
      // Update local subscription state with new data from response
      if (response.data.subscription) {
        setSubscription(response.data.subscription);
      } else {
        // Refresh subscription data if not returned
        await fetchData();
      }
      
      // Close confirmation dialog
      setShowCancelConfirm(false);
      
      // Show success message
      alert('Your subscription has been canceled. You are now on the Free plan.');
      
    } catch (err) {
      console.error('❌ [Subscription] Cancel error:', err);
      setError(err.response?.data?.error || 'Failed to cancel subscription. Please try again.');
    } finally {
      setCancelProcessing(false);
    }
  };

  // Handle manage subscription (open customer portal - for payment method updates)
  const handleManageSubscription = async () => {
    setBillingLoading(true);
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
      setBillingLoading(false);
    }
  };

  //Added: Helper function to determine button text and state for each plan
  const getPlanButtonProps = (planKey) => {
    const isCurrentPlan = subscription?.subscriptionPlan === planKey;
    const canCancel = subscription?.canCancel;
    const isFreePlan = planKey === 'free';
    
    const anyBusy = processingPlan !== null || billingLoading || cancelProcessing;
    let disabled = anyBusy;
    let buttonText = 'Select Plan';
    let buttonClass = '';
    
    if (isCurrentPlan) {
      disabled = true;
      buttonText = 'Current Plan';
      buttonClass = 'current';
    } else if (isFreePlan) {
      // Enable free button if user can cancel (is on paid plan with active subscription)
      if (canCancel) {
        disabled = false;
        buttonText = 'Downgrade to Free';
        buttonClass = 'downgrade';
      } else {
        disabled = true;
        buttonText = 'Free Forever';
        buttonClass = 'free-btn';
      }
    }
    
    return { disabled, buttonText, buttonClass };
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
                  <div className="plan-actions">
                    {/* Billing button - for payment methods, invoices */}
                    <button 
                      onClick={handleManageSubscription} 
                      className="manage-btn"
                      disabled={processingPlan !== null || billingLoading || cancelProcessing}
                      title="Update payment method, view invoices"
                    >
                      {billingLoading ? 'Loading...' : '💳 Billing'}
                    </button>
                  </div>
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
              {plans && Object.entries(plans).map(([key, plan]) => {
                const btnProps = getPlanButtonProps(key);
                
                return (
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
                      className={`plan-select-btn ${btnProps.buttonClass}`}
                      onClick={() => handleSelectPlan(key)}
                      disabled={btnProps.disabled}
                    >
                      {processingPlan === key || (cancelProcessing && key === 'free') ? 'Processing...' : btnProps.buttonText}
                    </button>
                  </div>
                );
              })}
            </div>

            {/* Footer note */}
            <p className="subscription-footer">
              🔒 Secure payments by Stripe • Cancel anytime
            </p>
          </>
        )}

        {/* Added: Cancel Confirmation Dialog */}
        {showCancelConfirm && (
          <div className="cancel-confirm-overlay" onClick={() => setShowCancelConfirm(false)}>
            <div className="cancel-confirm-dialog" onClick={(e) => e.stopPropagation()}>
              <h3>⚠️ Cancel Subscription?</h3>
              <p>Are you sure you want to cancel your <strong>{subscription?.subscriptionPlan?.toUpperCase()}</strong> subscription?</p>
              
              <div className="cancel-confirm-details">
                <p>You will lose access to:</p>
                <ul>
                  <li>📱 SMS notifications ({subscription?.smsCreditsLimit === -1 ? 'Unlimited' : subscription?.smsCreditsLimit + '/month'})</li>
                  <li>🤖 AI requests ({subscription?.aiRequestsLimit === -1 ? 'Unlimited' : subscription?.aiRequestsLimit + '/month'})</li>
                  <li>⭐ Priority support</li>
                </ul>
              </div>
              
              <p className="cancel-confirm-note">
                <strong>This action takes effect immediately.</strong>
              </p>
              
              <div className="cancel-confirm-buttons">
                <button 
                  className="cancel-confirm-btn cancel-yes"
                  onClick={handleCancelSubscription}
                  disabled={cancelProcessing}
                >
                  {cancelProcessing ? 'Canceling...' : 'Yes, Cancel Subscription'}
                </button>
                <button 
                  className="cancel-confirm-btn cancel-no"
                  onClick={() => setShowCancelConfirm(false)}
                  disabled={cancelProcessing}
                >
                  Keep My Plan
                </button>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

// ============ Upgrade Button Component ============
function UpgradeButton({ onClick, subscription }) {
  const isPremium = subscription?.isPremium;
  const plan = subscription?.subscriptionPlan;
  const planLabel = isPremium && plan ? plan.charAt(0).toUpperCase() + plan.slice(1) : 'Premium';
  
  return (
    <button 
      onClick={onClick} 
      className={`upgrade-btn ${isPremium ? 'premium' : ''}`}
      title={isPremium ? 'Manage subscription' : 'Upgrade to Premium'}
    >
      {isPremium ? '💎' : '⭐'} {isPremium ? planLabel : 'Upgrade'}
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