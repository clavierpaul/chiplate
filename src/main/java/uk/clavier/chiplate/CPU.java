package uk.clavier.chiplate;

import java.util.Random;

public class CPU {
    private Memory ram;
    private Display display;

    private int[] registers;
    private int i;
    private int[] stack;

    private int pc;
    private int sp;
    private int delayTimer;
    private int soundTimer;
    private int key;

    private boolean falseShift;

    private Random rd;

    public CPU(Memory ram, Display display, boolean falseShift) {
        this.ram = ram;
        this.display = display;

        this.registers = new int[16];
        this.i = 0;
        this.stack = new int[50];

        this.pc = 0x200;
        this.sp = -1;
        this.delayTimer = 0;
        this.soundTimer = 0;
        this.falseShift = falseShift;
        this.key = -1;

        this.rd = new Random(System.currentTimeMillis());
    }

    // debug mode dumps
    public int[] dumpRegisters() {
        return this.registers;
    }

    public int dumpPC() {
        return this.pc;
    }

    public int dumpOpcode() {
        return (this.ram.getByte(this.pc) << 8) + this.ram.getByte(this.pc + 1);
    }

    private int[] splitOpcode(int opcode) {
        int[] split = new int[4];

        split[0] = opcode & 0xF;
        split[1] = (opcode >> 4) & 0xF;
        split[2] = (opcode >> 8) & 0xF;
        split[3] = (opcode >> 12) & 0xF;

        return split;
    }
    
    public void setKey(int key) {
        this.key = key;
    }

    private void execute(int opcode) {
        int[] split = this.splitOpcode(opcode);

        int x = split[2];
        int y = split[1];
        int val = opcode & 0xFF;

        switch (split[3]) {
            case 0x0:
                if (opcode == 0x00E0) {
                    this.display.clear();

                    return;
                } else if (opcode == 0x00EE) {
                    this.pc = this.stack[this.sp];
                    this.sp--;
                
                    return;
                }

                System.out.println("0NNN called, panicking");
                System.exit(1);

            case 0x1:
                this.pc = opcode & 0xFFF;

                return;

            case 0x2:
                this.sp++;
                this.stack[this.sp] = this.pc;

                this.pc = opcode & 0xFFF;

                return;

            case 0x3:
                if (this.registers[x] == val) {
                    this.pc += 2;
                }
                
                return;

            case 0x4:
                if (this.registers[x] != val) {
                    this.pc += 2;
                }

                return;

            case 0x5:
                if (split[0] == 0) {
                    if (this.registers[x] == this.registers[y]) {
                        this.pc += 2;
                    }

                    return;
                }

                break;

            case 0x6:
                this.registers[x] = val;

                return;

            case 0x7:
                this.registers[x] = (this.registers[x] + val) & 0xFF; // byte wrap

                return;

            case 0x8:
                switch (split[0]) {
                    case 0x0:
                        this.registers[x] = this.registers[y];

                        return;
                    case 0x1:
                        this.registers[x] |= this.registers[y];

                        return;
                    case 0x2:
                        this.registers[x] &= this.registers[y];

                        return;
                    case 0x3:
                        this.registers[x] ^= this.registers[y];

                        return;
                    case 0x4: 
                        int addxy_res = this.registers[x] + this.registers[y];

                        if (addxy_res > 255) {
                            this.registers[0xF] = 1;
                        } else {
                            this.registers[0xF] = 0;
                        }

                        this.registers[x] = addxy_res & 0xFF;

                        return;
                    case 0x5:
                        int subxy_res = this.registers[x] - this.registers[y];

                        if (this.registers[x] < this.registers[y]) {
                            this.registers[0xF] = 0;
                        } else {
                            this.registers[0xF] = 1;
                        }

                        this.registers[x] = subxy_res & 0xFF;

                        return;
                    case 0x6:
                        if (this.falseShift) {
                            this.registers[0xF] = this.registers[x] & 1;
                            this.registers[x] = (this.registers[x] >> 1) & 0xFF;
                        } else {
                            this.registers[0xF] = this.registers[y] & 1;
                            this.registers[x] = (this.registers[y] >> 1) & 0xFF;
                        }

                        return;
                    case 0x7:
                        int subnxy_res = this.registers[y] - this.registers[x];

                        if (this.registers[y] < this.registers[x]) {
                            this.registers[0xF] = 0;
                        } else {
                            this.registers[0xF] = 1;
                        }

                        this.registers[x] = (byte) (subnxy_res & 0xFF);

                        return;
                    case 0xE:
                        if (this.falseShift) {
                            this.registers[0xF] = (byte) ((this.registers[x] >> 7) & 1);
                            this.registers[x] = (this.registers[x] << 1) & 0xFF;
                        } else {
                            this.registers[0xF] = (byte) ((this.registers[y] >> 7) & 1);
                            this.registers[x] = (byte) (this.registers[y] << 1) & 0xFF;
                        }

                        return;
                }

                break;

            case 0x9:
                if (split[0] == 0) {
                    if (this.registers[x] != this.registers[y]) {
                        this.pc += 2;
                    }

                    return;
                }

                break;

            case 0xA:
                this.i = opcode & 0xFFF;

                return;

            case 0xB:
                this.pc = (opcode & 0xFFF) + this.registers[0];

                return;

            case 0xC:
                this.registers[x] = this.rd.nextInt() & val;

                return;

            case 0xD:
                int sprite_x = registers[x] % 64;
                int sprite_y = registers[y] % 32;
                this.registers[0xF] = 0;

                for (int j = 0; j < (val & 0xF); ++j) {
                    for (int i = 0; i < 8; ++i) {
                        if (sprite_x + i < 64 && sprite_y + j < 32) {
                            byte to_draw = (byte) ((this.ram.getByte(this.i + j) >> (7 - i)) & 1);

                            if (this.display.setPixel(i + sprite_x, j + sprite_y, to_draw)) {
                                this.registers[0xF] = 1;
                            }
                        }
                    }
                }

                return;

            case 0xE:
                if (split[1] == 0x9 && split[0] == 0xE) {
                    if (this.registers[x] == this.key) {
                        this.pc += 2;
                    }

                    return;
                } else if (split[1] == 0xA && split[0] == 0x1) {
                    if (this.registers[x] != this.key) {
                        this.pc += 2;
                    }

                    return;
                }

                break;

            case 0xF:
                switch (opcode & 0xFF) {
                    case 0x07:
                        this.registers[x] = (byte) this.delayTimer;

                        return;
                    case 0x0A:
                        if (this.key == -1) {
                            this.pc -= 2;
                        } else {
                            this.registers[x] = this.key;
                        }

                        return;
                    case 0x15:
                        this.delayTimer = this.registers[x];

                        return;
                    case 0x18:
                        this.soundTimer = this.registers[x];

                        return;
                    case 0x1E:
                        this.i += this.registers[x];

                        return;
                    case 0x29:
                        this.i = this.registers[x] * 5;

                        return;
                    case 0x33:
                        int n = this.registers[x];

                        this.ram.setByte(this.i,     (byte) (Math.floor(n / 100)));
                        this.ram.setByte(this.i + 1, (byte) (Math.floor(n /  10) % 10));
                        this.ram.setByte(this.i + 2, (byte) (n % 10));

                        return;
                    case 0x55: 
                        for (int offset = 0; offset <= x; ++offset) {
                            this.ram.setByte(this.i + offset, (byte) this.registers[offset]);
                        }

                        return;
                    case 0x65:
                        for (int offset = 0; offset <= x; ++offset) {
                            this.registers[offset] = this.ram.getByte(this.i + offset);
                        }

                        return;
                }

                break;
        }

        System.out.println(String.format("Unknown instruction %04x called, panicking", opcode));
        System.exit(1);
    }

    public void doTimerTick() {
        if (this.delayTimer > 0) {
            this.delayTimer--;
        }

        if (this.soundTimer > 0) {
            this.soundTimer--;
        }
    }

    public void cycle() {
        int opcode = (this.ram.getByte(this.pc) << 8) + this.ram.getByte(this.pc + 1);
        this.pc += 2;

        this.execute(opcode);
    }
}