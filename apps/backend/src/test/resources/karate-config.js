function fn() {
  var env = karate.properties['karate.env'] || 'local';
  var urls = {
    local: 'http://localhost:8080',
    gcp:   'https://api-taskmanager.gcp.sriinfosoft.com',
    aws:   'https://api-taskmanager.sriinfosoft.com'
  };
  var config = {
    baseUrl: urls[env],
    testerUser: karate.properties['tester.user'] || 'admin',
    testerPassword: karate.properties['tester.password'] || ''
  };
  karate.configure('connectTimeout', 30000);
  karate.configure('readTimeout', 120000);
  // One login for the whole suite: callSingle caches across features.
  var auth = karate.callSingle('classpath:karate/auth.feature', config);
  config.token = auth.token;
  return config;
}
