var AeroJudgeFlightDirection = (function () {
    var key = 'aerojudge.flightDirection';

    function valid(direction) {
        return direction === 'B' || direction === 'C';
    }

    return {
        read: function (fallback) {
            var defaultDirection = valid(fallback) ? fallback : 'B';
            try {
                var stored = sessionStorage.getItem(key);
                return valid(stored) ? stored : defaultDirection;
            } catch (e) {
                return defaultDirection;
            }
        },
        remember: function (direction) {
            if (!valid(direction)) return;
            try {
                if (sessionStorage.getItem(key) !== direction) {
                    sessionStorage.setItem(key, direction);
                }
            } catch (e) {
                // Storage failure must not interrupt judging.
            }
        },
        reset: function () {
            try {
                sessionStorage.removeItem(key);
            } catch (e) {
                // Round creation has already succeeded; continue navigation.
            }
        }
    };
}());
