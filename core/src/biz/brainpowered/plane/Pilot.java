package biz.brainpowered.plane;

import biz.brainpowered.util.Util;
import com.badlogic.gdx.*;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.Timer;
import com.badlogic.gdx.utils.Timer.Task;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;

import java.util.ArrayList;

public class Pilot implements ApplicationListener
{

    public static final float DEFAULT_LIGHT_Z = 0.075f;
    public static final float AMBIENT_INTENSITY = 0.2f;
    public static final float LIGHT_INTENSITY = 1f;

    public static final Vector3 LIGHT_POS = new Vector3(0f,0f,DEFAULT_LIGHT_Z);
    //Light RGB and intensity (alpha)
    public static final Vector4f LIGHT_COLOR = new Vector4f(1f, 0.8f, 0.6f, 1f);

    //Ambient RGB and intensity (alpha)
    public static final Vector4f AMBIENT_COLOR = new Vector4f(0.6f, 0.6f, 1f, 0.2f);

    //Attenuation coefficients for light falloff
    public static final Vector3 FALLOFF = new Vector3(.4f, 3f, 20f);

    private ShaderProgram bumpShader;


    Debug debug;
    Plane plane;
    Ground ground;
    ExplosionFactory explosionFactory;
    ArrayList<Explosion> expCollection = new ArrayList<Explosion>();
    EnemyFactory enemyFactory1;
    EnemyFactory enemyFactory2;
    ArrayList<Enemy> enemyCollection = new ArrayList<Enemy>();
    BulletFactory bulletFactory;
    ArrayList<Bullet> bulletCollection = new ArrayList<Bullet>();

    // TODO: UI Class
    Texture titleImg; // top be implemented
    Texture planeNormals; // top be implemented
    Texture planeTex; // top be implemented

    // global config
    float planeScale = 0.5f;

    // TODO: Game Model
    Integer appWidth;
    Integer appHeight;
    String playerState;
    Integer score;
    float lastTimeScore;

    // SOUNDS:
    Sound pigeon;
    Enemy tmp;
    Camera camera;

    //used to make the light flicker
    public float zAngle;
    public static final float zSpeed = 15.0f;
    public static final float PI2 = 3.1415926535897932384626433832795f * 2.0f;

    private boolean	lightMove = false;
    private boolean lightOscillate = false;
    private FrameBuffer fbo;

    //read our shader files
    private String vertPass;
    private String vertexShader;
    private String defaultPixelShader;
    private String finalPixelShader;
    private ShaderProgram defaultShader;
    private ShaderProgram finalShader;

    //values passed to the shader
    public static final float ambientIntensity = .99f;
    public static final Vector3 ambientColor = new Vector3(0.99f, 0.99f, 0.99f);

    // Shadow Casting
    private int lightSize = 256;
    private float upScale = 1.0f; //for example; try lightSize=128, upScale=1.5f

    SpriteBatch batch;
    OrthographicCamera cam;
    BitmapFont font;

    TextureRegion shadowMap1D; //1 dimensional shadow map
    TextureRegion occluders;   //occluder map
    TextureRegion finalLightMap;   //occluder map

    FrameBuffer shadowMapFBO;
    FrameBuffer occludersFBO;

    FrameBuffer finalLightMapFBO;
    Texture finalLightMapTex;

    Texture casterSprites;
    Texture light;

    ShaderProgram shadowMapShader, shadowRenderShader;

    Array<Light> lights = new Array<Light>();

    boolean additive = true;
    boolean softShadows = true;

    /**
     * Compiles a new instance of the default shader for this batch and returns it. If compilation
     * was unsuccessful, GdxRuntimeException will be thrown.
     * @return the default shader
     */
    public static ShaderProgram createShader(String vert, String frag) {
        ShaderProgram prog = new ShaderProgram(vert, frag);
        if (!prog.isCompiled())
            throw new GdxRuntimeException("could not compile shader: " + prog.getLog());
        if (prog.getLog().length() != 0)
            Gdx.app.log("GpuShadows", prog.getLog());
        return prog;
    }

    @Override
    public void create ()
    {
        // viewport
        appWidth = Gdx.graphics.getWidth();
        appHeight = Gdx.graphics.getHeight();
        camera = new OrthographicCamera(appWidth, appHeight);
        camera.update();
        batch = new SpriteBatch();

        // Explosion Sound
        pigeon = Gdx.audio.newSound(Gdx.files.internal("pigeon.mp3"));

        // Refactored Classes Init Here
        debug = new Debug();

        //read our shader files
        vertPass = Gdx.files.internal("shaders/shadow/vertpass.glsl").readString();
        vertexShader = Gdx.files.internal("shaders/vertexShader.glsl").readString();
        defaultPixelShader = Gdx.files.internal("shaders/defaultPixelShader.glsl").readString();
        finalPixelShader =  Gdx.files.internal("shaders/pixelShader.glsl").readString();

        bumpShader = createShader(vertexShader, Gdx.files.internal("shaders/bumpFrag.glsl").readString());


        // asset manager to load asset config and generate types of Config objects
        PlaneConfig pc = new PlaneConfig();
        pc.normalTP = "airplane/PLANE_8_N.png";
        pc.leftTP = "airplane/PLANE_8_L.png";
        pc.rightTP = "airplane/PLANE_8_R.png";
        pc.scale = planeScale;

        plane = new Plane(pc, bulletCollection);
        plane.init();

        ground = new Ground("airplane/airPlanesBackground.png", 80.0f);

        explosionFactory = new ExplosionFactory("explosion19.png", 5, 5, 1/25f, lights);
        explosionFactory.init();

        enemyFactory1 = new EnemyFactory("airplane/PLANE_1_N.png", planeScale);
        enemyFactory1.init();
        enemyFactory2 = new EnemyFactory("airplane/PLANE_2_N.png", planeScale);
        enemyFactory2.init();

        bulletFactory = new BulletFactory("airplane/B_2.png", 0.5f, lights);
        bulletFactory.init();
        plane.setBulletFactory(bulletFactory);

        ShaderProgram.pedantic = false;
        defaultShader = new ShaderProgram(vertexShader, defaultPixelShader);
        finalShader = new ShaderProgram(vertexShader, finalPixelShader);

        finalShader.begin();
        finalShader.setUniformi("u_lightmap", 1); //  1 value refers to the FBO binding
        finalShader.setUniformf("ambientColor", ambientColor.x, ambientColor.y,
                ambientColor.z, ambientIntensity);
        finalShader.end();

        light = new Texture("light.png");
        planeNormals = new Texture(Gdx.files.internal("airplane/PLANE_8_N_NRM.png"));

        playerState = PlayerState.NORMAL; // TODO: Can use real enums too

        // TODO: move Score Models into GameManager
        lastTimeScore = 0.0f;
        score = 0;

        // Shadow Map Setup
        //read vertex pass-through shader
        final String VERT_SRC = Gdx.files.internal("shaders/shadow/vertpass.glsl").readString();

        planeTex = new Texture(Gdx.files.internal("airplane/PLANE_8_N.png"));

        //setup default uniforms
        bumpShader.begin();

        //our normal map
        bumpShader.setUniformi("u_normals", 1); //GL_TEXTURE1

        //light/ambient colors
        //LibGDX doesn't have Vector4 class at the moment, so we pass them individually...
        bumpShader.setUniformf("LightColor", LIGHT_COLOR.x, LIGHT_COLOR.y, LIGHT_COLOR.z, LIGHT_INTENSITY);
        bumpShader.setUniformf("AmbientColor", AMBIENT_COLOR.x, AMBIENT_COLOR.y, AMBIENT_COLOR.z, AMBIENT_INTENSITY);
        bumpShader.setUniformf("Falloff", FALLOFF);

        //LibGDX likes us to end the shader program
        bumpShader.end();


        // renders occluders to 1D shadow map
        shadowMapShader = createShader(VERT_SRC, Gdx.files.internal("shaders/shadow/shadowMap.glsl").readString());
        // samples 1D shadow map to create the blurred soft shadow
        shadowRenderShader = createShader(VERT_SRC, Gdx.files.internal("shaders/shadow/shadowRender.glsl").readString());

        //the occluders
        casterSprites = new Texture("explosion19.png");
        //the light sprite
        light = new Texture("light.png");

        //build frame buffers
        occludersFBO = new FrameBuffer(Pixmap.Format.RGBA8888, lightSize, lightSize, false);
        occluders = new TextureRegion(occludersFBO.getColorBufferTexture());
        occluders.flip(false, true);

        //our 1D shadow map, lightSize x 1 pixels, no depth
        shadowMapFBO = new FrameBuffer(Pixmap.Format.RGBA8888, lightSize, 1, false);
        Texture shadowMapTex = shadowMapFBO.getColorBufferTexture();

        //use linear filtering and repeat wrap mode when sampling
        shadowMapTex.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        shadowMapTex.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);

        //for debugging only; in order to render the 1D shadow map FBO to screen
        shadowMap1D = new TextureRegion(shadowMapTex);
        shadowMap1D.flip(false, true);

        finalLightMapFBO = new FrameBuffer(Pixmap.Format.RGBA8888, appWidth, appHeight, false);
        finalLightMapTex = finalLightMapFBO.getColorBufferTexture();

        finalLightMap = new TextureRegion(finalLightMapTex);
        finalLightMap.flip(false, true);

        font = new BitmapFont();

        cam = new OrthographicCamera(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        cam.setToOrtho(false);
//
//        Gdx.input.setInputProcessor(new InputAdapter() {
//
//            public boolean touchDown(int x, int y, int pointer, int button) {
//
//                System.out.println("x:"+Gdx.input.getX()+" y:"+Gdx.input.getY());
//                float mx = x;
//                float my = Gdx.graphics.getHeight() - y;
//                lights.add(new Light(mx, my, Light.randomColor()));
//                return true;
//            }
//
//            public boolean keyDown(int key) {
//                if (key== Input.Keys.SPACE){
//                    clearLights();
//                    return true;
//                } else if (key== Input.Keys.A){
//                    additive = !additive;
//                    return true;
//                } else if (key== Input.Keys.S){
//                    softShadows = !softShadows;
//                    return true;
//                }
//                return false;
//            }
//        });

        //clearLights();
//
//        // control management
//        directionKeyReleased = true;

        scheduleEnemies();
    }

    private void scheduleEnemies(){
        Timer.schedule(new Task() {
            @Override
            public void run() {
                EnemiesAndClouds();
            }
        }, 0, 0.5f);
    }

    private void EnemiesAndClouds()
    {
        // TODO: Determine condition for creating new Enemies
        Integer GoOrNot = Math.round((float)Math.random());
        //System.out.println("GoOrNot: "+GoOrNot);
        if(GoOrNot == 1){
            Enemy enemy;

            //int randomEnemy = Util.getRandomNumberBetween(0, 1);
            double rand = Math.random();
            if(rand < 0.5f)
                enemy = enemyFactory1.create(appWidth/2, appHeight/2);
            else
                enemy = enemyFactory2.create(appWidth/2, appHeight/2);

            enemy.initPath(appWidth, appHeight);
            enemyCollection.add(enemy);
        }
    }

    // todo: more disposal
    public void dispose() {
        // post refactor
        debug.dispose();
        plane.dispose();
        ground.dispose();
        explosionFactory.dispose();

        // pre-refactor
        batch.dispose();
        titleImg.dispose();
    }

    @Override
    public void render ()
    {
        // Lighting Rendering HEre
        final float dt = Gdx.graphics.getRawDeltaTime();

        // Occilation angle
        zAngle += dt * zSpeed;
        while(zAngle > PI2)
            zAngle -= PI2;

        //draw the light to the FBO
//        fbo.begin();
//        batch.setShader(defaultShader); // passthrough
//        Gdx.gl.glClearColor(0f, 0f, 0f, 0f); // clear with white opaque
//        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
//        batch.begin();
//
//        // TODO:  Explosion Class to implement light source
//        boolean lightOscillate = true;
//        float lightSize = lightOscillate? (100.00f + (25.0f * (float)Math.sin(zAngle)) + 25.0f* MathUtils.random()): 150.0f;
//        //lightSize = 150f;
//        for (int x = 0; x<expCollection.size(); x++)
//        {
//            batch.draw(light,
//                    expCollection.get(x)._x + 32 - (lightSize*0.5f),
//                    expCollection.get(x)._y + 32 - (lightSize*0.5f),
//                    lightSize,
//                    lightSize);
//            // todo: light size to change over time
//        }
//        for (int x = 0; x<bulletCollection.size(); x++)
//        {
//            batch.draw(light,
//                    bulletCollection.get(x)._x + 6 - (lightSize*0.5f),
//                    bulletCollection.get(x)._y + 16 - (lightSize*0.5f),
//                    lightSize,
//                    lightSize);
//        }
//        batch.end();
//        fbo.end();





        // Shadow Rendering HEre
        //clear frame
        Gdx.gl.glClearColor(0.25f,0.25f,0.25f,1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        if (additive)
            batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
        for (int i=0; i<lights.size; i++)
        {
            renderLight(lights.get(i));
        }
        if (additive)
            batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);






        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        batch.setShader(finalShader);
        batch.begin();
        finalLightMapFBO.getColorBufferTexture().bind(1); //this is important! bind the FBO to the 2nd texture unit
        light.bind(0); //we force the binding of a texture on first texture unit to avoid artefacts
        //this is because our default and ambiant shader dont use multi texturing...
        //you can basically bind anything, it doesnt matter

        ground.render(batch);
        //timeScore(); // todo scoring in game model

        // Post-Refactor
        // Debug
        debug.draw(batch, "Score: "+score);
        debug.draw(batch, "Planes: "+enemyCollection.size());
        debug.draw(batch, "Explosions: "+expCollection.size());
        debug.draw(batch, "Lights: "+lights.size);
        debug.reset();

        plane.render(batch);

        // Bullets
        for (int x = 0; x<bulletCollection.size(); x++)
        {
            bulletCollection.get(x).render(batch);
        }

        // Enemy Render and Collision Detection
        for (int x = 0; x<enemyCollection.size(); x++)
        {
            enemyCollection.get(x).updatePos();
            enemyCollection.get(x).render(batch);

            for (int y = 0; y<bulletCollection.size(); y++)
            {
                if(enemyCollection.get(x).checkOverlap(bulletCollection.get(y).getRectangle()))
                {
                    // sound
                    pigeon.play();
                    enemyCollection.get(x).setDispose();
                    bulletCollection.get(y).setDispose();
                    tmp = enemyCollection.get(x);

                    expCollection.add(explosionFactory.create((tmp._x + (tmp.sprite.getWidth()/2) - 50), tmp._y + (tmp.sprite.getHeight()/2) - 50));
                    //expCollectionSize++;
                    score += 250000;
                }
            }

            if(enemyCollection.get(x).checkOverlap(plane.getBoundingBox()))
            {
                // sound
                pigeon.play();
                // plane dies!
            }
        }

        for (int x = 0; x<expCollection.size(); x++)
        {
            expCollection.get(x).render(batch);
        }

        // Batch Rendering Done
        batch.end();

        // BUMP
        //update light position, normalized to screen resolution

//        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

//        //reset light Z
//        if (Gdx.input.isTouched()) {
//            LIGHT_POS.z = DEFAULT_LIGHT_Z;
//            System.out.println("New light Z: "+LIGHT_POS.z);
//        }

        //shader will now be in use...

        //update light position, normalized to screen resolution
//        float gx = 100 / appWidth;
//        float gy = 100 / appHeight;
//
//        LIGHT_POS.x = gx;
//        LIGHT_POS.y = gy;
//
//        //float[] numbers = new float[]();
//        float[] myIntArray = new float[(lights.size +1) * 3];
//        int ex = 0;
//        for(int i =0; i<lights.size; i++)
//        {
//            myIntArray[ex] = lights.get(i).x;
//            ex++;
//            myIntArray[ex] = lights.get(i).y;
//            ex++;
//            myIntArray[ex] = DEFAULT_LIGHT_Z;
//            ex++;
//        }
//
//        myIntArray[ex] = gx;
//        ex++;
//        myIntArray[ex] = gy;
//        ex++;
//        myIntArray[ex] = DEFAULT_LIGHT_Z;
//        ex++;
//
//        //send a Vector4f to GLSL
//        bumpShader.setUniformf("LightPos", LIGHT_POS);
//        bumpShader.setUniform3fv("LightPoss", myIntArray, 0, ex);
//        bumpShader.setUniformi("lightCount", lights.size + 1);
//
//        batch.setShader(bumpShader);
//        batch.begin();
//        //bind normal map to texture unit 1
//        // or render normals to texture, then bind
//
//        planeNormals.bind(1);
//
//        //bind diffuse color to texture unit 0
//        //important that we specify 0 otherwise we'll still be bound to glActiveTexture(GL_TEXTURE1)
//        planeTex.bind(0);
//
//        //draw the texture unit 0 with our shader effect applied
//        batch.draw(planeTex, 50, 50);
//
//        batch.end();
        // END BUMP


        // Empty out the Light Map once per frame
        finalLightMapFBO.begin();
        Gdx.gl.glClearColor(0f,0f,0f,0f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        finalLightMapFBO.end();

        // GC after rendering
        for (int x = 0; x<enemyCollection.size(); x++)
        {
            if(enemyCollection.get(x)._dispose)
            {
                enemyCollection.get(x).dispose();
                enemyCollection.remove(x);
            }
        }

        for (int x = 0; x<expCollection.size(); x++)
        {
            if (expCollection.get(x).elapsedTime > 1.0f){
                expCollection.get(x).dispose();
                expCollection.remove(x);
            }
        }

        for (int x = 0; x<bulletCollection.size(); x++)
        {
            if (bulletCollection.get(x)._dispose){
                bulletCollection.get(x).dispose();
                bulletCollection.remove(x);
            }
        }

        // A little Hack with arrays (kind of forcing the GC here)
        Util.cleanNulls(enemyCollection);
        Util.cleanNulls(expCollection);
        Util.cleanNulls(bulletCollection);
    }

    void renderLight(Light o)
    {
        float mx = o.x;
        float my = o.y;
        float lightSize = this.lightSize;//o.scale *

        //STEP 1. render light region to occluder FBO

        //bind the occluder FBO
        occludersFBO.begin();

        //clear the FBO to WHITE/ TRANSPARENT
        Gdx.gl.glClearColor(0f,0f,0f,0f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        //set the orthographic camera to the size of our FBO
        cam.setToOrtho(false, occludersFBO.getWidth(), occludersFBO.getHeight());

        //translate camera so that light is in the center
        cam.translate(mx - lightSize/2f, my - lightSize/2f);

        //update camera matrices
        cam.update();

        //set up our batch for the occluder pass
        batch.setProjectionMatrix(cam.combined);
        batch.setShader(null); //use default shader
        batch.begin();

        // ... draw any sprites that will cast shadows here ... //
        for (int x = 0; x<enemyCollection.size(); x++) {
            enemyCollection.get(x).render(batch);
        }

        //end the batch before unbinding the FBO
        batch.end();

        //unbind the FBO
        occludersFBO.end();

        //STEP 2. build a 1D shadow map from occlude FBO

        //bind shadow map
        shadowMapFBO.begin();

        //clear it
        Gdx.gl.glClearColor(0f,0f,0f,0f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        //set our shadow map shader
        batch.setShader(shadowMapShader);
        batch.begin();
        shadowMapShader.setUniformf("resolution", lightSize, lightSize);
        shadowMapShader.setUniformf("upScale", upScale);

        //reset our projection matrix to the FBO size
        cam.setToOrtho(false, shadowMapFBO.getWidth(), shadowMapFBO.getHeight());
        batch.setProjectionMatrix(cam.combined);

        //draw the occluders texture to our 1D shadow map FBO
        batch.draw(occluders.getTexture(), 0, 0, lightSize, shadowMapFBO.getHeight());

        //flush batch
        batch.end();

        //unbind shadow map FBO
        shadowMapFBO.end();

        //STEP 3. render the blurred shadows

        finalLightMapFBO.begin();
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        //reset projection matrix to screen !!!
        cam.setToOrtho(false);
        batch.setProjectionMatrix(cam.combined);

        //set the shader which actually draws the light/shadow
        batch.setShader(shadowRenderShader);
        batch.begin();

        shadowRenderShader.setUniformf("resolution", lightSize, lightSize);
        shadowRenderShader.setUniformf("softShadows", softShadows ? 1f : 0f);

        //set color to light
        batch.setColor(o.color);
        float finalSize = lightSize * o.scale;

        //draw centered on light position
        batch.draw(shadowMap1D.getTexture(), mx-finalSize/2f, my-finalSize/2f, finalSize, finalSize);

        //flush the batch before swapping shaders into the FrameBuffer
        batch.end();
        finalLightMapFBO.end();

        //reset color
        batch.setColor(Color.WHITE);
    }

    // todo: figure out a Score Model in the Game Manager
    public void timeScore(float timeElapsed)
    {
        if ((lastTimeScore + 1.0f) < (timeElapsed))
        {
            score += 100;
            lastTimeScore = timeElapsed;
        }
    }

    @Override
    public void resize(final int width, final int height)
    {
        //Shading (2d)
        cam.setToOrtho(false, width, height);
        batch.setProjectionMatrix(cam.combined);

        bumpShader.begin();
        bumpShader.setUniformf("Resolution", width, height);
        bumpShader.end();

        // todo: check if FBOs need to be re-inited

        // Lighting
        //fbo = new FrameBuffer(Pixmap.Format.RGBA8888, width, height, false);

        finalShader.begin();
        finalShader.setUniformf("resolution", width, height);
        finalShader.end();
    }

    @Override
    public void pause() {

    }

    @Override
    public void resume() {
        //initPlane();
    }
}
