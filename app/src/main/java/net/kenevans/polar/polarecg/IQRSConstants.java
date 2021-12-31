package net.kenevans.polar.polarecg;

interface IQRSConstants {
    /**
     * Sampling rate.  These algorithms are based on this particular sampling
     * rate.
     */
    double FS = 130.0;
    /**
     * Data window size.  Must be large enough for maximum number of
     * coefficients.
     */
    int DATA_WINDOW = 20;
    /**
     * Moving average window size.
     */
    int MOV_AVG_WINDOW = 20;
    /**
     * Moving average height window size.
     */
    int MOV_AVG_HEIGHT_WINDOW = 5;
    /**
     * Moving average height default.
     */
    double MOV_AVG_HEIGHT_DEFAULT = .025;
    /**
     * Moving average height threshold factor.
     * Note: threshold = MOV_AVG_HEIGHT_THRESHOLD_FACTOR * Moving_average.avg()
     */
    double MOV_AVG_HEIGHT_THRESHOLD_FACTOR = .4;
    /**
     * The group delay, used for searching ecg for maxima.
     * Coefficients: bandpass=7, deriv=4, square=1, average=20.
     * Delay is (N-1)/2, N = number of coefficients for FIR filter.
     * Varies 5 - 18 for Butterworth fs=130 low_cutoff=5 high_cutoff=20.
     * 18 chosen empirically.
     */
    int SCORE_OFFSET = 18;
    /**
     * Filter coefficients for Butterworth fs=130 low_cutoff=5 high_cutoff=20
     */
    double[] A_BUTTERWORTH3 = {1.0, -4.026234474291334,
            7.118704187414651,
            -7.142612123715484, 4.314550872956459,
            -1.4837877480823038, 0.2259301306922936,};
    double[] B_BUTTERWORTH3 = {0.025966345753506013, 0.0,
            -0.07789903726051804,
            0.0, 0.07789903726051804, 0.0, -0.025966345753506013,};
    /**
     * Filter coefficients for Pan Tompkins derivative
     */
    double[] A_DERIVATIVE = {1};
    double[] B_DERIVATIVE = {0.5, 0.25, -0.25, -0.5};


}
