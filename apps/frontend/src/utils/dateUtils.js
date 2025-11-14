// src/utils/dateUtils.js
// SUPER DEBUG VERSION

export const convertLocalToUTC = (localDateTimeString) => {
    console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━');
    console.log('🔍 convertLocalToUTC called!');
    console.log('📥 Input:', localDateTimeString);
    console.log('📥 Input type:', typeof localDateTimeString);
    
    if (!localDateTimeString) {
      console.log('⚠️ Input is null/undefined, returning null');
      console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━');
      return null;
    }
    
    console.log('✅ Input is valid, processing...');
    
    // Create Date object
    const localDate = new Date(localDateTimeString);
    console.log('📅 Local Date object:', localDate);
    console.log('📅 Local Date string:', localDate.toString());
    
    // Get UTC components
    const year = localDate.getUTCFullYear();
    const month = String(localDate.getUTCMonth() + 1).padStart(2, '0');
    const day = String(localDate.getUTCDate()).padStart(2, '0');
    const hours = String(localDate.getUTCHours()).padStart(2, '0');
    const minutes = String(localDate.getUTCMinutes()).padStart(2, '0');
    const seconds = String(localDate.getUTCSeconds()).padStart(2, '0');
    
    console.log('🔢 UTC Components:', { year, month, day, hours, minutes, seconds });
    
    // Format as LocalDateTime (NO 'Z', NO milliseconds)
    const utcFormatted = `${year}-${month}-${day}T${hours}:${minutes}:${seconds}Z`;
    
    console.log('📤 Output (UTC formatted):', utcFormatted);
    console.log('📤 Output type:', typeof utcFormatted);
    console.log('📤 Output length:', utcFormatted.length);
    console.log('📤 Contains .000Z?', utcFormatted.includes('.000Z') ? '❌ YES (BAD!)' : '✅ NO (GOOD!)');
    console.log('📤 Contains Z?', utcFormatted.includes('Z') ? '❌ YES (BAD!)' : '✅ NO (GOOD!)');
    console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━');
    
    return utcFormatted;
  };
  
  export const getUserTimezone = () => {
    return Intl.DateTimeFormat().resolvedOptions().timeZone;
  };
  
  export const getUTCOffset = () => {
    return -new Date().getTimezoneOffset() / 60;
  };
  
  // Log on import
  console.log('═══════════════════════════════════════');
  console.log('🌍 dateUtils.js loaded!');
  console.log('🌍 User timezone:', getUserTimezone());
  console.log('⏰ UTC offset:', getUTCOffset() >= 0 ? `+${getUTCOffset()}` : getUTCOffset(), 'hours');
  console.log('═══════════════════════════════════════');
  
  // Test the function immediately
  console.log('\n🧪 TESTING convertLocalToUTC with sample date:');
  const testResult = convertLocalToUTC('2025-11-13T20:00');
  console.log('🧪 Test result:', testResult);
  console.log('');