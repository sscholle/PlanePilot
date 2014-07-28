package biz.brainpowered.plane.comp;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;

/**
 * @deprecated in favour of specialised GroupInterfaces
 */
public interface ComponentGroupInterface {

    // initialise this type of Component Group
    public void init ();

    // add component instance (which has reference to the 'owning' Entity)
    public void addComponent ( ComponentInterface componentInterface );

    // for graphics
    // todo: rather split up the ComponentGroupTypes, only specifying thier preferred u[date method
    //public void updateGroup ( SpriteBatch batch );

}