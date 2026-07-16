const http = require('http');

const options = {
  hostname: 'localhost',
  port: 8080,
  path: '/api/auth/login',
  method: 'POST',
  headers: {
    'Content-Type': 'application/json'
  }
};

const req = http.request(options, (res) => {
  let data = '';
  res.on('data', (chunk) => data += chunk);
  res.on('end', () => {
    const token = JSON.parse(data).token;
    
    // Get Central Store ID
    http.get('http://localhost:8080/api/stores?managedOnly=false', { headers: { Authorization: `Bearer ${token}` } }, (res2) => {
        let storeData = '';
        res2.on('data', (chunk) => storeData += chunk);
        res2.on('end', () => {
            const stores = JSON.parse(storeData);
            const storesArr = Array.isArray(stores) ? stores : stores.content;
            const central = storesArr.find(s => s.type === 'CENTRAL');
            
            // Get Central Store Stock
            http.get(`http://localhost:8080/api/reports/current-stock?storeId=${central.id}`, { headers: { Authorization: `Bearer ${token}` } }, (res3) => {
                let stockData = '';
                res3.on('data', (chunk) => stockData += chunk);
                res3.on('end', () => {
                    console.log(stockData);
                });
            });
        });
    });
  });
});

req.write(JSON.stringify({ email: 'takudzwachitsungo2@gmail.com', password: 'password123' }));
req.end();
