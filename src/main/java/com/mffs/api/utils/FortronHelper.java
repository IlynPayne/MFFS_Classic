package com.mffs.api.utils;

import com.mffs.ModularForcefieldSystem;
import com.mffs.api.fortron.IFortronFrequency;
import com.mffs.api.modules.IModuleAcceptor;
import com.mffs.api.vector.Vector3D;
import com.mffs.common.TransferMode;
import com.mffs.common.items.modules.projector.ItemModuleCamouflage;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import java.util.Iterator;
import java.util.Set;

/**
 * @author Calclavia
 */
public class FortronHelper
{

    /**
     * Transfers fortron from 1 FortronFreqency, to multiple others.
     *
     * @param freq  The base frequency that is being looked for.
     * @param tiles The other tiles to send frequencies to.
     * @param mode  The current mode to send.
     * @param limit The tick limit.
     */
    public static void transfer(IFortronFrequency freq, Set<IFortronFrequency> tiles, TransferMode mode, int limit)
    {
        int fortron = freq.getFortronEnergy(), capacity = freq.getFortronCapacity();
        for (Iterator<IFortronFrequency> it$ = tiles.iterator(); it$.hasNext(); )
        {
            IFortronFrequency machine = it$.next();
            if (machine == null)
            {//this should prevent nulls.
                it$.remove();
                continue;
            }

            fortron += machine.getFortronEnergy();
            capacity += machine.getFortronCapacity();
        }

        if (fortron <= 0 || capacity <= 0)
        {
            return;
        }

        switch (mode)
        {
            case DISTRIBUTE:
                //spread energy evenly to all machines (amount-based)
                for (IFortronFrequency machine : tiles)
                {
                    transfer(freq, machine, (fortron / tiles.size()) - machine.getFortronEnergy(), limit);
                }
                return;

            case FILL:
                //fill the capacitor and EQUALIZE the remaining energy

                if (freq.getFortronEnergy() < freq.getFortronCapacity())
                {
                    //drain energy from other machines
                    for (IFortronFrequency machine : tiles)
                    {
                        //It doesn't matter how much energy is actaully transfered,
                        //it will be equalized when an internal container is full.
                        transfer(machine, freq, limit, limit);
                    }
                    return;
                }

            case DRAIN:
                //similar to EQUALIZE but without storing energy in this machine

                //tiles.add(freq);//we wanna equally send to ourselves.

                if (mode.equals(TransferMode.DRAIN) && (freq.getFortronEnergy() > 1000 || tiles.size() < 2))
                {
                    //First empty internal container; spread energy just like in DISTRIBUTE mode.
                    for (IFortronFrequency machine : tiles)
                    {
                        //it doesn't matter how much energy is actaully transfered,
                        //it will be equalized when an internal container is empty.
                        transfer(freq, machine, limit, limit);
                    }
                }
                else
                {
                    //now spread the energy evenly between other machines, same as in EQUALIZE mode.
                    IFortronFrequency[] machines = new IFortronFrequency[tiles.size()];
                    tiles.toArray(machines);

                    for (int i = 0; i < machines.length - 1; i++)
                    {
                        int num = machines[i + 1].getFortronCapacity() * machines[i].getFortronEnergy()
                                    - machines[i].getFortronCapacity() * machines[i + 1].getFortronEnergy();
                        int den = machines[i].getFortronCapacity() + machines[i + 1].getFortronEnergy();
                        int transfer = (int) Math.floor(num / den + 0.5f);

                        //energy is actually transmitted bewteen two machines without the FortronCapacitor, so a custom
                        //beams must be drawn.
                        transfer(machines[i], machines[i + 1], transfer, limit, freq);
                    }
                }
                return;

            case EQUALIZE:
                //spread energy evenly to all machines (procentage-based)

                //tiles.remove(freq);
                for (IFortronFrequency machine : tiles)
                {
                    int transfer = (int) (((double) machine.getFortronCapacity() / capacity) * fortron) - machine.getFortronEnergy();
                    transfer(freq, machine, transfer, limit);
                }
        }
    }

    /**
     * Transfers fortron directly from 1 machine, to the receiving.
     *
     * @param freq  The sending machine.
     * @param rec   The receiving machine.
     * @param joul  The jouls to be sent.
     * @param limit The limit per tick.
     * @param intermediate optional intermediate machine beam will be drawn through
     */
    public static void transfer(IFortronFrequency freq, IFortronFrequency rec, int joul, int limit, IFortronFrequency intermediate)
    {
        TileEntity entity = (TileEntity) freq;
        World world = entity.getWorldObj();
        boolean camo = (freq instanceof IModuleAcceptor && ((IModuleAcceptor) freq).getModuleCount(ItemModuleCamouflage.class) > 0);

        if (joul < 0)
        { //we switch the frequencies! Means they have less than the receiver
            IFortronFrequency dummy = freq;
            freq = rec;
            rec = dummy;
        }

        joul = Math.min(joul < 0 ? Math.abs(joul) : joul, limit);
        int toBeInject = rec.provideFortron(freq.requestFortron(joul, false), false);
        toBeInject = freq.requestFortron(rec.provideFortron(toBeInject, true), true);

        if (world.isRemote && toBeInject > 0 && !camo)
        {
            if (intermediate == null)
            {
                ModularForcefieldSystem.proxy.registerBeamEffect(world, new Vector3D((TileEntity) freq).translate(.5), new Vector3D((TileEntity) rec).translate(.5), 0.6F, 0.6F, 1, 20);
            }
            else
            {
                //draw beams: freq <--> intermediate and intermediate <--> rec
                Vector3D interVector = new Vector3D((TileEntity) intermediate).translate(.5);
                ModularForcefieldSystem.proxy.registerBeamEffect(world, new Vector3D((TileEntity) freq).translate(.5),
                        interVector,0.6F, 0.6F, 1, 20);
                ModularForcefieldSystem.proxy.registerBeamEffect(world, interVector,
                        new Vector3D((TileEntity) rec).translate(.5), 0.6F, 0.6F, 1, 20);
            }
        }
    }

    /**
     * Same as {@link #transfer(IFortronFrequency, IFortronFrequency, int, int, IFortronFrequency)} with last param
     * set to <i>null</i>.
     */
    public static void transfer(IFortronFrequency freq, IFortronFrequency rec, int joul, int limit)
    {
        transfer(freq, rec, joul, limit, null);
    }
}
