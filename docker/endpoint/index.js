const pg = require('pg');
const router = require('koa-router')();
const logger = require('koa-logger');
const cors = require('@koa/cors');
const Koa = require('koa');

const config = {
    user: process.env.PG_USER,
    host: process.env.PG_URL,
    database: process.env.PG_DB,
    port: process.env.PG_PORT
};
const pool = new pg.Pool(config);

async function index(ctx) {
    ctx.body = "";
}

async function getPatients(ctx) {
    const partialPatientId = ctx.query.partialPatientId;
    if (!partialPatientId) ctx.throw(400, 'partialPatientId');

    const client = await pool.connect();

    const from = new Date().getTime() - 1000 * 60 * 60 * 24;

    try {
        const patientIdsResult = await client.query(`
        SELECT patient_id
        FROM patient_ids
        WHERE MATCH(patient_id_ft, $1) AND patient_id LIKE $2;
        `, [partialPatientId, `%${partialPatientId}%`]);

        const patientIds = patientIdsResult.rows.map(r => r.patient_id)

        // This will not lead to SQL injections because patientIds are internal
        const queryResult = await client.query(`
        SELECT DISTINCT ar.patient_id, ac.alert_count
        FROM aggregated_records AS ar
               LEFT JOIN (SELECT patient_id, COUNT(*) as alert_count
                          FROM alerts
                          WHERE patient_id IN (${patientIds.map(p => `'${p}'`).join(',')})
                          GROUP BY patient_id) AS ac ON ac.patient_id = ar.patient_id
        WHERE ar.patient_id IN (${patientIds.map(p => `'${p}'`).join(',')})
          AND ar.ts >= $1
        ORDER BY ac.alert_count;
        `, [from])

        ctx.body = queryResult.rows;
    } catch (err) {
        console.error(err);
    } finally {
        await client.release();
    }
}

async function getRecentPatients(ctx) {
    const client = await pool.connect();

    const from = new Date().getTime() - 1000 * 60 * 15;

    try {
        const queryResult = await client.query(`
        SELECT patient_id, COUNT(*) AS alert_count
        FROM alerts
        WHERE ts >= $1
        GROUP BY patient_id
        ORDER BY alert_count DESC
        LIMIT 100;
        `, [from]);

        ctx.body = queryResult.rows;
    } catch (err) {
        console.error(err);
    } finally {
        await client.release();
    }
}

async function getPatientRecords(ctx) {
    const patientId = ctx.query.patientId;
    if (!patientId) ctx.throw(400, 'patientId');

    const now = new Date().getTime();

    const from = parseInt(ctx.query.from) || now - 1000 * 60 * 60;
    if (typeof from != 'number') ctx.throw(400, 'from');

    const to = parseInt(ctx.query.to) || now;
    if (typeof to != 'number') ctx.throw(400, 'to');

    const client = await pool.connect();

    try {
        const queryResult = await client.query(`
        SELECT id, patient_id, CAST(ts as LONG) AS ts, u, avg_v, min_v, max_v
        FROM aggregated_records
        WHERE patient_id = $1 AND ts >= $2 AND ts <= $3;
        `, [patientId, from, to]);

        ctx.body = queryResult.rows;
    } catch (err) {
        console.error(err);
    } finally {
        await client.release();
    }
}

async function getPatientAlerts(ctx) {
    const patientId = ctx.query.patientId;
    if (!patientId) ctx.throw(400, 'patientId');

    const now = new Date().getTime();

    const from = parseInt(ctx.query.from) || now - 1000 * 60 * 60;
    if (typeof from != 'number') ctx.throw(400, 'from');

    const to = parseInt(ctx.query.to) || now;
    if (typeof to != 'number') ctx.throw(400, 'to');

    const client = await pool.connect();

    try {
        const queryResult = await client.query(`
        SELECT id, patient_id, CAST(ts as LONG) AS ts, u, v
        FROM alerts
        WHERE patient_id = $1 AND ts >= $2 AND ts <= $3;
        `, [patientId, from, to]);

        ctx.body = queryResult.rows;
    } catch (err) {
        console.error(err);
    } finally {
        await client.release();
    }
}

async function getPatientAdwinAlerts(ctx) {
    const patientId = ctx.query.patientId;
    if (!patientId) ctx.throw(400, 'patientId');

    const now = new Date().getTime();

    const from = parseInt(ctx.query.from) || now - 1000 * 60 * 60;
    if (typeof from != 'number') ctx.throw(400, 'from');

    const to = parseInt(ctx.query.to) || now;
    if (typeof to != 'number') ctx.throw(400, 'to');

    const client = await pool.connect();

    try {
        const queryResult = await client.query(`
        SELECT id, patient_id, u, CAST(f as LONG) AS f, CAST(t as LONG) AS t
        FROM adwin_alerts
        WHERE patient_id = $1 AND f >= $2 AND f <= $3;
        `, [patientId, from, to]);

        ctx.body = queryResult.rows;
    } catch (err) {
        console.error(err);
    } finally {
        await client.release();
    }
}

async function start() {
    const app = new Koa();

    app.use(logger());
    app.use(cors());

    router

        .get('/', index)

        .get('/api/getPatients', getPatients)
        .get('/api/getRecentPatients', getRecentPatients)
        .get('/api/getPatientRecords', getPatientRecords)
        .get('/api/getPatientAlerts', getPatientAlerts)
        .get('/api/getPatientAdwinAlerts', getPatientAdwinAlerts);

    app.use(router.routes());

    app.listen(80);

    console.log('This application is listening 80.');
}

start().then(() => {
});