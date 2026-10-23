package app.padmapper;

oneway interface IEvdevCallback {
    void onAxis(int code, float value);
    void onRel(int dx, int dy);
    void onButton(int code, boolean down);
}
