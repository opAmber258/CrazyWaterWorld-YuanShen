import http.client
import json


class BlockbenchMCP:
    def __init__(self, host='::1', port=3000, timeout=120):
        self.conn = http.client.HTTPConnection(host, port, timeout=timeout)
        self.session_id = None
        self.rid = 0
        self._initialize()

    def _request(self, method, params=None, notify=False):
        self.rid += 1
        payload = {'jsonrpc': '2.0', 'method': method}
        if not notify:
            payload['id'] = self.rid
        if params is not None:
            payload['params'] = params
        headers = {
            'Content-Type': 'application/json',
            'Accept': 'application/json, text/event-stream',
            'Host': '[::1]:3000',
        }
        if self.session_id:
            headers['mcp-session-id'] = self.session_id
        self.conn.request('POST', '/bb-mcp', body=json.dumps(payload), headers=headers)
        response = self.conn.getresponse()
        raw = response.read().decode()
        if 'mcp-session-id' in response.headers:
            self.session_id = response.headers['mcp-session-id']
        if response.status >= 400:
            raise RuntimeError(f'MCP HTTP {response.status}: {raw}')
        return json.loads(raw) if raw else None

    def _initialize(self):
        self._request('initialize', {
            'protocolVersion': '2025-03-26',
            'capabilities': {},
            'clientInfo': {'name': 'codex-desktop', 'version': '1.0'},
        })
        self._request('notifications/initialized', {}, notify=True)

    def call_tool(self, name, arguments=None):
        response = self._request('tools/call', {
            'name': name,
            'arguments': arguments or {},
        })
        result = response.get('result', {})
        content = result.get('content', [])
        text_items = [item.get('text', '') for item in content if item.get('type') == 'text']
        if result.get('isError'):
            raise RuntimeError('\n'.join(text_items))
        if text_items:
            text = text_items[0]
            try:
                return json.loads(text)
            except json.JSONDecodeError:
                return text
        return result

    def eval_js(self, code):
        return self.call_tool('risky_eval', {'code': code})
