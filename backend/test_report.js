const fs = require('fs');

async function test() {
    try {
        const loginRes = await fetch('http://localhost:8080/api/auth/login', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ email: 'takudzwachitsungo2@gmail.com', password: 'password123' })
        });
        const loginData = await loginRes.json();
        console.log("Login:", loginData);
        if (!loginData.token) return;

        const token = loginData.token;

        const storesRes = await fetch('http://localhost:8080/api/stores?managedOnly=false', {
            headers: { 'Authorization': 'Bearer ' + token }
        });
        const storesData = await storesRes.json();
        const storesArr = Array.isArray(storesData) ? storesData : storesData.content;
        const central = storesArr.find(s => s.type === 'CENTRAL');
        console.log("Central store:", central);

        if (central) {
            const reportRes = await fetch('http://localhost:8080/api/reports/current-stock?storeId=' + central.id, {
                headers: { 'Authorization': 'Bearer ' + token }
            });
            const reportData = await reportRes.json();
            console.log("Report data:", reportData);
        }
    } catch (e) {
        console.error(e);
    }
}

test();
