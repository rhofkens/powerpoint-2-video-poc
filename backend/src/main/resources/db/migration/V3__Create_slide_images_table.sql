-- Create slide_images table
CREATE TABLE IF NOT EXISTS slide_images (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    slide_id UUID NOT NULL,
    image_path VARCHAR(500) NOT NULL,
    image_type VARCHAR(20) NOT NULL,
    width INTEGER,
    height INTEGER,
    order_in_slide INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_slide_images_slide 
        FOREIGN KEY (slide_id) REFERENCES slides(id) 
        ON DELETE CASCADE
);

-- Create index on slide_id for joins
CREATE INDEX IF NOT EXISTS idx_slide_images_slide_id ON slide_images(slide_id);

-- Create index on order_in_slide for ordering
CREATE INDEX IF NOT EXISTS idx_slide_images_order ON slide_images(slide_id, order_in_slide);