# Health Check Endpoint

## GET /api/health

Returns the current status of the backend service.

### Response

```json
{
  "status": "UP"
}
```

### Status Codes

- `200 OK` - Service is healthy and running
- `500 Internal Server Error` - Service is experiencing issues

### Example Usage

```bash
curl http://localhost:8080/api/health
```

```javascript
const response = await fetch('/api/health');
const health = await response.json();
console.log(health); // { status: "UP" }
```