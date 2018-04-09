/*
 * File:   main.c
 * Author: pierre muth
 *
 * Created on 17 février 2018, 13:42
 */


// PIC18F87K90 Configuration Bit Settings
// CONFIG1L
#pragma config RETEN = ON       // VREG Sleep Enable bit (Enabled)
#pragma config INTOSCSEL = HIGH // LF-INTOSC Low-power Enable bit (LF-INTOSC in High-power mode during Sleep)
#pragma config SOSCSEL = HIGH   // SOSC Power Selection and mode Configuration bits (High Power SOSC circuit selected)
#pragma config XINST = OFF       // Extended Instruction Set (Disabled)
// CONFIG1H
#pragma config FOSC = INTIO2    // Oscillator (Internal RC oscillator)
#pragma config PLLCFG = ON      // PLL x4 Enable bit (Enabled)
#pragma config FCMEN = OFF      // Fail-Safe Clock Monitor (Disabled)
#pragma config IESO = OFF       // Internal External Oscillator Switch Over Mode (Disabled)
// CONFIG2L
#pragma config PWRTEN = OFF     // Power Up Timer (Disabled)
#pragma config BOREN = SBORDIS  // Brown Out Detect (Enabled in hardware, SBOREN disabled)
#pragma config BORV = 3         // Brown-out Reset Voltage bits (1.8V)
#pragma config BORPWR = ZPBORMV // BORMV Power level (ZPBORMV instead of BORMV is selected)
// CONFIG2H
#pragma config WDTEN = OFF      // Watchdog Timer (WDT disabled in hardware; SWDTEN bit disabled)
#pragma config WDTPS = 1048576  // Watchdog Postscaler (1:1048576)
// CONFIG3L
#pragma config RTCOSC = SOSCREF // RTCC Clock Select (RTCC uses SOSC)
// CONFIG3H
#pragma config CCP2MX = PORTC   // CCP2 Mux (RC1)
#pragma config ECCPMX = PORTE   // ECCP Mux (Enhanced CCP1/3 [P1B/P1C/P3B/P3C] muxed with RE6/RE5/RE4/RE3)
#pragma config MSSPMSK = MSK7   // MSSP address masking (7 Bit address masking mode)
#pragma config MCLRE = OFF      // Master Clear Enable (MCLR Disabled, RG5 Enabled)
// CONFIG4L
#pragma config STVREN = ON      // Stack Overflow Reset (Enabled)
#pragma config BBSIZ = BB2K     // Boot Block Size (2K word Boot Block size)
// CONFIG5L
#pragma config CP0 = OFF        // Code Protect 00800-03FFF (Disabled)
#pragma config CP1 = OFF        // Code Protect 04000-07FFF (Disabled)
#pragma config CP2 = OFF        // Code Protect 08000-0BFFF (Disabled)
#pragma config CP3 = OFF        // Code Protect 0C000-0FFFF (Disabled)
#pragma config CP4 = OFF        // Code Protect 10000-13FFF (Disabled)
#pragma config CP5 = OFF        // Code Protect 14000-17FFF (Disabled)
#pragma config CP6 = OFF        // Code Protect 18000-1BFFF (Disabled)
#pragma config CP7 = OFF        // Code Protect 1C000-1FFFF (Disabled)
// CONFIG5H
#pragma config CPB = OFF        // Code Protect Boot (Disabled)
#pragma config CPD = OFF        // Data EE Read Protect (Disabled)
// CONFIG6L
#pragma config WRT0 = OFF       // Table Write Protect 00800-03FFF (Disabled)
#pragma config WRT1 = OFF       // Table Write Protect 04000-07FFF (Disabled)
#pragma config WRT2 = OFF       // Table Write Protect 08000-0BFFF (Disabled)
#pragma config WRT3 = OFF       // Table Write Protect 0C000-0FFFF (Disabled)
#pragma config WRT4 = OFF       // Table Write Protect 10000-13FFF (Disabled)
#pragma config WRT5 = OFF       // Table Write Protect 14000-17FFF (Disabled)
#pragma config WRT6 = OFF       // Table Write Protect 18000-1BFFF (Disabled)
#pragma config WRT7 = OFF       // Table Write Protect 1C000-1FFFF (Disabled)
// CONFIG6H
#pragma config WRTC = OFF       // Config. Write Protect (Disabled)
#pragma config WRTB = OFF       // Table Write Protect Boot (Disabled)
#pragma config WRTD = OFF       // Data EE Write Protect (Disabled)
// CONFIG7L
#pragma config EBRT0 = OFF      // Table Read Protect 00800-03FFF (Disabled)
#pragma config EBRT1 = OFF      // Table Read Protect 04000-07FFF (Disabled)
#pragma config EBRT2 = OFF      // Table Read Protect 08000-0BFFF (Disabled)
#pragma config EBRT3 = OFF      // Table Read Protect 0C000-0FFFF (Disabled)
#pragma config EBRT4 = OFF      // Table Read Protect 10000-13FFF (Disabled)
#pragma config EBRT5 = OFF      // Table Read Protect 14000-17FFF (Disabled)
#pragma config EBRT6 = OFF      // Table Read Protect 18000-1BFFF (Disabled)
#pragma config EBRT7 = OFF      // Table Read Protect 1C000-1FFFF (Disabled)
// CONFIG7H
#pragma config EBRTB = OFF      // Table Read Protect Boot (Disabled)

// includes
#include <xc.h>

// CPU freq
#define _XTAL_FREQ  (64000000UL)

// status
#define IDLE 0
#define UPDATING 1
#define BYPASS 3
#define LED PORTFbits.RF7
#define ANODES PORTA
#define CATHODES PORTE
#define A_SELECT PORTJ
#define K_SELECT PORTH

// proto
void init(void);
void screen_scan();

// global vars
unsigned char received_byte = 0;
unsigned char status = 0;
unsigned char write_screen = 0;
unsigned char read_screen = 0;
unsigned char page_counter = 0;
unsigned char column_counter = 0;

unsigned short screen_read_pointer = 0;
unsigned short screen_write_pointer = 0;

unsigned char screen_mem_1[512];
unsigned char screen_mem_2[512];

void interrupt isr(void) {
    if (PIR5bits.TMR4IF) {
        PIR5bits.TMR4IF = 0;
        screen_scan();
    }
}

void main(void) {
    init();

    LED = 1;
    __delay_ms(1000);
    LED = 0;
    
    while (1){
        if (PIR3bits.RC2IF) {       // serial byte received
            received_byte = RCREG2;
            PIR3bits.RC2IF = 0;
            
            screen_mem_1[screen_write_pointer] = received_byte;
            
            screen_write_pointer++;
            if (screen_write_pointer > 511) screen_write_pointer = 0;
            
            LED = !LED;
        }
    }
}

void screen_scan() {
    
    screen_read_pointer = (column_counter*8);
    
    K_SELECT = 0xFF;
    CATHODES = 0b1 << (column_counter%8);
    __delay_us(10);
    
    K_SELECT = ~(0b1 << (column_counter>>3));
    
    A_SELECT = 0b11111110;
    ANODES = screen_mem_1[screen_read_pointer+0];
    __delay_us(8);
    ANODES = 0;
    
    A_SELECT = 0b11111101;
    ANODES = screen_mem_1[screen_read_pointer+1];
    __delay_us(8);
    ANODES = 0;
    
    A_SELECT = 0b11111011;
    ANODES = screen_mem_1[screen_read_pointer+2];
    __delay_us(8);
    ANODES = 0;
    
    A_SELECT = 0b11110111;
    ANODES = screen_mem_1[screen_read_pointer+3];
    __delay_us(8);
    ANODES = 0;
    
    A_SELECT = 0b11101111;
    ANODES = screen_mem_1[screen_read_pointer+4];
    __delay_us(8);
    ANODES = 0;
    
    A_SELECT = 0b11011111;
    ANODES = screen_mem_1[screen_read_pointer+5];
    __delay_us(8);
    ANODES = 0;
    
    A_SELECT = 0b10111111;
    ANODES = screen_mem_1[screen_read_pointer+6];
    __delay_us(10);
    ANODES = 0;
    
    A_SELECT = 0b01111111;
    ANODES = screen_mem_1[screen_read_pointer+7];
    __delay_us(10);
    ANODES = 0;
    
    A_SELECT = 0b11111111;
    
    
    column_counter++;
    if (column_counter > 63) column_counter = 0;
    
}

void init(){
    // main clock
    OSCCONbits.IRCF = 0b111; // 16MHz clock
    OSCTUNEbits.PLLEN = 1;   // x4 PLL = 64MHz  
    
    // watchdog
    WDTCONbits.SWDTEN = 0;  // disalbe
    
    // GPIO
    ANCON0 = 0x00;      // no analog
    ANCON1 = 0x00;
    ANCON2 = 0x00;
    
    TRISFbits.TRISF7 = 0;   // F7 as output
    
    TRISA = 0x00;       // PORTA as output
    TRISE = 0x00;       // PORTE as output
    TRISH = 0x00;       // PORTH as output
    TRISJ = 0x00;       // PORTJ as output

    // Serial 2
    ANCON2bits.ANSEL18 = 0;
    ANCON2bits.ANSEL19 = 0;
    TRISGbits.TRISG2 = 1;
    TRISGbits.TRISG1 = 0;
    RCSTA2bits.SPEN2 = 1;
    RCSTA2bits.CREN2 = 1;
    TXSTA2bits.BRGH2 = 1;
    BAUDCON2bits.BRG162 = 1;
    SPBRG2 = 0x8A;       // 115200 bauds @Fosc 64Mhz
    SPBRGH2 = 0x00;
    PIR3bits.RC2IF = 0;
    
    // timer
    T4CONbits.T4CKPS = 0b11;    // x16 prescaler
    PR4 = 254;      // 156* (62.5ns*16) = 156us
    T4CONbits.TMR4ON = 1;
    PIR5bits.TMR4IF = 0;
    PIE5bits.TMR4IE = 1;
    
    // general interrupt
    INTCONbits.PEIE = 1;
    INTCONbits.GIE = 1;
    
    int i;
    for (i = 0; i < 512; i++) {
        screen_mem_1[i] = 0x55 << (((i/8)+1)%2);
    }
    
}