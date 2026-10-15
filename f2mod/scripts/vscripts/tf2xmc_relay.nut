// tf2xmc_relay.nut — HTTP relay client for TF2 x Minecraft passthrough
// Uses Northstar's built-in HTTP API (NSHttpRequest, NSHttpPostBody, etc.)
// Requires launch argument: -allowlocalhttp (to reach 127.0.0.1)

// === Configuration (from ConVars) ===

string function Tf2xmc_GetRelayUrl() {
    string host = GetConVarString("tf2xmc_relay_host");
    int port = GetConVarInt("tf2xmc_relay_port");
    return "http://" + host + ":" + port;
}

// === State ===

bool g_tf2xmc_connected = false;
string g_tf2xmc_relay_url = "";
float g_tf2xmc_last_poll = 0.0;
table g_tf2xmc_last_state = {};
array g_tf2xmc_event_queue = {};

// === Connection management ===

bool function Tf2xmc_IsConnected() {
    return g_tf2xmc_connected;
}

bool function Tf2xmc_ConnectToRelay() {
    g_tf2xmc_relay_url = Tf2xmc_GetRelayUrl();

    bool success = false;
    NSHttpGet(g_tf2xmc_relay_url + "/health",
        void function(HttpRequestResponse resp) {
            if (NSIsSuccessHttpCode(resp.statusCode)) {
                g_tf2xmc_connected = true;
                printt("TF2 x Minecraft: relay connected at " + g_tf2xmc_relay_url + "\n");
            } else {
                printt("TF2 x Minecraft: relay returned " + resp.statusCode + "\n");
            }
        },
        void function(HttpRequestFailure fail) {
            printt("TF2 x Minecraft: relay connection failed: " + fail.errorMessage + "\n");
        }
    );

    return success;
}

void function Tf2xmc_DisconnectFromRelay() {
    g_tf2xmc_connected = false;
    g_tf2xmc_relay_url = "";
}

// === Send TF2 state to relay ===

void function Tf2xmc_SendTf2State(string json) {
    if (!g_tf2xmc_connected || g_tf2xmc_relay_url == "") return;

    NSHttpPostBody(g_tf2xmc_relay_url + "/tf2/state", json,
        void function(HttpRequestResponse resp) {
            // Success
        },
        void function(HttpRequestFailure fail) {
            g_tf2xmc_connected = false;
        }
    );
}

// === Send TF2 event to relay ===

void function Tf2xmc_SendTf2Event(string type, table payload) {
    if (!g_tf2xmc_connected) return;

    table evt = {};
    evt["type"] <- type;
    evt["payload"] <- payload;
    string json = EncodeJSON(evt);

    NSHttpPostBody(g_tf2xmc_relay_url + "/tf2/event", json,
        void function(HttpRequestResponse resp) {},
        void function(HttpRequestFailure fail) {}
    );
}

// === Poll for MC events ===

array function Tf2xmc_PollMcEvents() {
    array result = {};
    if (!g_tf2xmc_connected) return result;

    float now = Time();
    if (now - g_tf2xmc_last_poll < 0.1) return result;
    g_tf2xmc_last_poll = now;

    NSHttpGet(g_tf2xmc_relay_url + "/events",
        void function(HttpRequestResponse resp) {
            if (NSIsSuccessHttpCode(resp.statusCode)) {
                table data = DecodeJSON(resp.body);
                if ("events" in data) {
                    g_tf2xmc_event_queue = data["events"];
                }
            }
        },
        void function(HttpRequestFailure fail) {}
    );

    array temp = g_tf2xmc_event_queue;
    g_tf2xmc_event_queue = {};
    return temp;
}

// === Get MC state ===

table function Tf2xmc_GetMcState() {
    table result = {};
    if (!g_tf2xmc_connected) return result;

    NSHttpGet(g_tf2xmc_relay_url + "/mc/state",
        void function(HttpRequestResponse resp) {
            if (NSIsSuccessHttpCode(resp.statusCode)) {
                result = DecodeJSON(resp.body);
            }
        },
        void function(HttpRequestFailure fail) {}
    );

    return result;
}

// === Print state ===

void function Tf2xmc_PrintState() {
    if (g_tf2xmc_last_state.len() == 0) {
        printt("TF2 x Minecraft: no state yet\n");
        return;
    }
    float x = g_tf2xmc_last_state["x"];
    float y = g_tf2xmc_last_state["y"];
    float z = g_tf2xmc_last_state["z"];
    printt("TF2 x Minecraft: pos(" + x + ", " + y + ", " + z + ")\n");
}