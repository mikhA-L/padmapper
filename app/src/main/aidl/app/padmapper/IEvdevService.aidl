package app.padmapper;

import app.padmapper.IEvdevCallback;

interface IEvdevService {
    void destroy() = 16777114;
    void start(IEvdevCallback cb) = 1;
    void stop() = 2;
}
